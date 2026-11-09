/*
 * Copyright (c) 2026, David Uhden Collado
 * Copyright (c) 2022-2026, r4v3r23
 * Copyright (c) 2017-2022, m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Derived from Monerujo (https://github.com/m2049r/xmrwallet) and modified
 * for ANONERO. See NOTICE.
 */

#include <inttypes.h>
#include <cstring>
#include <mutex>
#include <atomic>
#include <memory>
#include <new>
#include "anonero.h"
#include "wallet2_api.h"

//TODO explicit casting jlong, jint, jboolean to avoid warnings

#ifdef __cplusplus
extern "C"
{
#endif

#define LOG_TAG "WalletNDK"
#ifdef __ANDROID__
#include <android/log.h>
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG,__VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG  , LOG_TAG,__VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO   , LOG_TAG,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN   , LOG_TAG,__VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR  , LOG_TAG,__VA_ARGS__)
#else
// Desktop has no logcat; same five macros over stderr.
#include <stdio.h>
#define ANON_LOG(lvl, ...) do { \
        fprintf(stderr, "%s/" LOG_TAG ": ", lvl); \
        fprintf(stderr, __VA_ARGS__); \
        fputc('\n', stderr); \
    } while (0)
#define LOGV(...) ANON_LOG("V", __VA_ARGS__)
#define LOGD(...) ANON_LOG("D", __VA_ARGS__)
#define LOGI(...) ANON_LOG("I", __VA_ARGS__)
#define LOGW(...) ANON_LOG("W", __VA_ARGS__)
#define LOGE(...) ANON_LOG("E", __VA_ARGS__)
#endif

// Android's jni.h declares AttachCurrentThread(JNIEnv**); the desktop JDK's takes void**.
#ifdef __ANDROID__
#define ANON_ATTACH_ARG(e) (e)
#else
#define ANON_ATTACH_ARG(e) reinterpret_cast<void **>(e)
#endif

/*
 * JNI boundary overview
 * ---------------------
 * Kotlin declares the ABI as `external` methods; every declaration must have a matching
 * Java_io_anonero_* export (checked statically by scripts/check-jni-contract.py, but
 * signatures and linkage are verified only by a real build).
 *
 * - `jlong` handles are raw pointers to wallet2 objects. Kotlin owns the wrapper, while the
 *   native wallet owns the pointed-to object; closing/replacing the wallet invalidates the
 *   handle. Never persist or send a handle.
 * - Local references are per JNI call. The classes cached in JNI_OnLoad and the listener's
 *   jlistener are global references and must live until the process or listener is done.
 * - Wallet2 calls may block on disk, network, or refresh work. Callbacks run on wallet2's
 *   refresh thread; the adapter attaches it to the JVM and serializes callback delivery.
 * - Errors are returned as status values, null/zero handles, or wallet2 error strings. No
 *   C++ exception may cross the boundary; catch at the export and fail closed.
 * - Key material stays inside wallet2. Do not add logging of passwords, seeds, private
 *   keys, proxy credentials, or Keystore material.
 */
static JavaVM *cachedJVM;
static jclass class_ArrayList;
static jclass class_WalletListener;
static jclass class_TransactionInfo;
static jclass class_Transfer;
static jclass class_WalletStatus;
static jclass class_CoinsInfo;
std::mutex _listenerMutex;

static jclass cacheGlobalClass(JNIEnv *env, const char *name) {
    jclass local = env->FindClass(name);
    if (local == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jclass global = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    if (global == nullptr && env->ExceptionCheck()) env->ExceptionClear();
    return global;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    (void) reserved;
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    //LOGI("JNI_OnLoad ok");

    class_ArrayList = cacheGlobalClass(jenv, "java/util/ArrayList");
    class_TransactionInfo = cacheGlobalClass(jenv, "io/anonero/model/TransactionInfo");
    class_Transfer = cacheGlobalClass(jenv, "io/anonero/model/Transfer");
    class_WalletListener = cacheGlobalClass(jenv, "io/anonero/model/WalletListener");
    class_WalletStatus = cacheGlobalClass(jenv, "io/anonero/model/Wallet$Status");
    class_CoinsInfo = cacheGlobalClass(jenv, "io/anonero/model/CoinsInfo");

    if (class_ArrayList == nullptr || class_TransactionInfo == nullptr ||
        class_Transfer == nullptr || class_WalletListener == nullptr ||
        class_WalletStatus == nullptr || class_CoinsInfo == nullptr) {
        LOGE("JNI_OnLoad: required Kotlin class is missing");
        auto release = [jenv](jclass &c) {
            if (c != nullptr) {
                jenv->DeleteGlobalRef(c);
                c = nullptr;
            }
        };
        release(class_ArrayList);
        release(class_TransactionInfo);
        release(class_Transfer);
        release(class_WalletListener);
        release(class_WalletStatus);
        release(class_CoinsInfo);
        return -1;
    }
    return JNI_VERSION_1_6;
}
#ifdef __cplusplus
}
#endif

int attachJVM(JNIEnv **jenv) {
    int envStat = cachedJVM->GetEnv((void **) jenv, JNI_VERSION_1_6);
    if (envStat == JNI_EDETACHED) {
        if (cachedJVM->AttachCurrentThread(ANON_ATTACH_ARG(jenv), nullptr) != 0) {
            LOGE("Failed to attach");
            return JNI_ERR;
        }
    } else if (envStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return JNI_ERR;
    }
    //LOGI("envStat=%i", envStat);
    return envStat;
}

void detachJVM(JNIEnv *jenv, int envStat) {
    //LOGI("envStat=%i", envStat);
    if (jenv->ExceptionCheck()) {
        LOGE("Wallet callback JNI operation failed");
        jenv->ExceptionClear();
    }

    if (envStat == JNI_EDETACHED) {
        cachedJVM->DetachCurrentThread();
    }
}

class ScopedCallbackEnv {
public:
    ScopedCallbackEnv() : env_(nullptr), status_(attachJVM(&env_)) {}
    ~ScopedCallbackEnv() noexcept {
        if (status_ != JNI_ERR && env_ != nullptr) detachJVM(env_, status_);
    }

    ScopedCallbackEnv(const ScopedCallbackEnv &) = delete;
    ScopedCallbackEnv &operator=(const ScopedCallbackEnv &) = delete;

    JNIEnv *get() const noexcept { return status_ == JNI_ERR ? nullptr : env_; }

private:
    JNIEnv *env_;
    int status_;
};

class ScopedLocalRef {
public:
    ScopedLocalRef(JNIEnv *env, jobject ref) noexcept : env_(env), ref_(ref) {}
    ~ScopedLocalRef() noexcept {
        if (ref_ != nullptr) env_->DeleteLocalRef(ref_);
    }

    ScopedLocalRef(const ScopedLocalRef &) = delete;
    ScopedLocalRef &operator=(const ScopedLocalRef &) = delete;

private:
    JNIEnv *env_;
    jobject ref_;
};

/*
 * Native listener adapter. One instance is allocated by setListenerJ and owns a global
 * reference to the Kotlin WalletListener. _listenerMutex protects that global reference;
 * callbacks take a local reference under the lock and then call Kotlin without holding it,
 * so close/replace cannot deadlock against a re-entrant listener. The adapter attaches the
 * wallet2 thread only for the duration of a callback and detaches it only if it attached it.
 */
struct MyWalletListener : Monero::WalletListener {
    jobject jlistener;

    MyWalletListener(JNIEnv *env, jobject aListener) {
        LOGD("Created Listener");
        jlistener = env->NewGlobalRef(aListener);
        if (jlistener == nullptr && env->ExceptionCheck()) env->ExceptionClear();
    }

    ~MyWalletListener() {
        LOGD("Destroyed Listener");
    }

    void deleteGlobalJavaRef(JNIEnv *env) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener != nullptr) {
            env->DeleteGlobalRef(jlistener);
            jlistener = nullptr;
        }
    }

/*
 * @brief updated  - generic callback, called when any event (sent/received/block reveived/etc) happened with the wallet;
 */
    void updated() {
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jobject listener = nullptr;
        {
            std::lock_guard<std::mutex> lock(_listenerMutex);
            if (jlistener == nullptr) return;
            listener = jenv->NewLocalRef(jlistener);
        }
        if (listener == nullptr) {
            if (jenv->ExceptionCheck()) jenv->ExceptionClear();
            return;
        }
        ScopedLocalRef listenerRef(jenv, listener);

        jmethodID listenerClass_updated = jenv->GetMethodID(class_WalletListener, "updated", "()V");
        if (listenerClass_updated == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(listener, listenerClass_updated);
    }


    /*
     * @brief moneySpent - called when money spent
     * @param txId       - transaction id
     * @param amount     - amount
     */
    void moneySpent(const std::string &txId, uint64_t amount) {
        (void) txId;
        (void) amount;
        // Sensitive amounts and transaction ids are not logged.
    }

    /*
     * @brief moneyReceived - called when money received
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void moneyReceived(const std::string &txId, uint64_t amount) {
        (void) txId;
        (void) amount;
        // Sensitive amounts and transaction ids are not logged.
    }

    /*
     * @brief unconfirmedMoneyReceived - called when payment arrived in tx pool
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void unconfirmedMoneyReceived(const std::string &txId, uint64_t amount) {
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jobject listener = nullptr;
        {
            std::lock_guard<std::mutex> lock(_listenerMutex);
            if (jlistener == nullptr) return;
            listener = jenv->NewLocalRef(jlistener);
        }
        if (listener == nullptr) {
            if (jenv->ExceptionCheck()) jenv->ExceptionClear();
            return;
        }
        ScopedLocalRef listenerRef(jenv, listener);

        jlong amountLong = static_cast<jlong>(amount);
        jstring jstring1 = jenv->NewStringUTF(txId.c_str());
        ScopedLocalRef txIdRef(jenv, jstring1);
        if (jstring1 == nullptr || jenv->ExceptionCheck()) return;

        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener,
                                                             "unconfirmedMoneyReceived",
                                                             "(Ljava/lang/String;J)V");
        if (listenerClass_newBlock == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(listener, listenerClass_newBlock, jstring1, amountLong);
    }

    /*
     * @brief newBlock      - called when new block received
     * @param height        - block height
     */
    void newBlock(uint64_t height) {
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jobject listener = nullptr;
        {
            std::lock_guard<std::mutex> lock(_listenerMutex);
            if (jlistener == nullptr) return;
            listener = jenv->NewLocalRef(jlistener);
        }
        if (listener == nullptr) {
            if (jenv->ExceptionCheck()) jenv->ExceptionClear();
            return;
        }
        ScopedLocalRef listenerRef(jenv, listener);

        jlong h = static_cast<jlong>(height);
        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener, "newBlock",
                                                             "(J)V");
        if (listenerClass_newBlock == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(listener, listenerClass_newBlock, h);
    }

/*
 * @brief refreshed - called when wallet refreshed by background thread or explicitly refreshed by calling "refresh" synchronously
 */
    void refreshed() {
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jobject listener = nullptr;
        {
            std::lock_guard<std::mutex> lock(_listenerMutex);
            if (jlistener == nullptr) return;
            listener = jenv->NewLocalRef(jlistener);
        }
        if (listener == nullptr) {
            if (jenv->ExceptionCheck()) jenv->ExceptionClear();
            return;
        }
        ScopedLocalRef listenerRef(jenv, listener);

        jmethodID listenerClass_refreshed = jenv->GetMethodID(class_WalletListener, "refreshed",
                                                              "()V");
        if (listenerClass_refreshed == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(listener, listenerClass_refreshed);
    }
};


//// helper methods
class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv *env, jstring value)
            : env_(env), value_(value),
              chars_(value == nullptr ? nullptr : env->GetStringUTFChars(value, nullptr)) {}

    ~ScopedUtfChars() noexcept {
        if (chars_ != nullptr) env_->ReleaseStringUTFChars(value_, chars_);
    }

    ScopedUtfChars(const ScopedUtfChars &) = delete;
    ScopedUtfChars &operator=(const ScopedUtfChars &) = delete;

    const char *get() const noexcept { return chars_; }

private:
    JNIEnv *env_;
    jstring value_;
    const char *chars_;
};

// Construct only after successfully acquiring the flag; a rejected caller must not reset it.
class ScopedAtomicFlagReset {
public:
    explicit ScopedAtomicFlagReset(std::atomic<bool> &flag) noexcept : flag_(flag) {}
    ~ScopedAtomicFlagReset() noexcept { flag_.store(false); }

    ScopedAtomicFlagReset(const ScopedAtomicFlagReset &) = delete;
    ScopedAtomicFlagReset &operator=(const ScopedAtomicFlagReset &) = delete;

private:
    std::atomic<bool> &flag_;
};

jlong getElement(JNIEnv *env, jlongArray arr_j, int element) {
    jlong result = 0;
    if (arr_j == nullptr) return result;
    if (env->GetLongArrayRegion(arr_j, element, 1, &result) != 0) {
        env->ExceptionClear();
        return 0;
    }
    return result;
}

std::vector<std::uint64_t> java2cpp_long(JNIEnv *env, jlongArray longArray) {
    std::vector<std::uint64_t> result;
    if (longArray == nullptr) return result;
    jint len = env->GetArrayLength(longArray);
    if (len < 0) return result;
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        jlong amount = getElement(env, longArray, i);
        // Amounts are unsigned in wallet2; zero and negative jlong values are invalid
        // input. Reject the whole array so a failed read cannot become a zero payment.
        if (amount <= 0) return {};
        result.emplace_back(static_cast<std::uint64_t>(amount));
    }
    return result;
}

std::string java2cpp_bytes(JNIEnv *env, jbyteArray array) {
    if (array == nullptr) return std::string();
    jsize len = env->GetArrayLength(array);
    if (len < 0) return std::string();
    std::string result;
    result.resize(len);
    if (len > 0) {
        if (env->GetByteArrayRegion(array, 0, len,
                                    reinterpret_cast<jbyte *>(&result[0])) != 0) {
            env->ExceptionClear();
            return std::string();
        }
    }
    return result;
}

std::vector<std::string> java2cpp_string_array(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    jsize len = env->GetArrayLength(array);
    if (len < 0) return result;
    result.reserve(len);
    for (jsize i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (element == nullptr) {
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
                return {};
            }
            result.emplace_back("");
            continue;
        }
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        if (pchars == nullptr) {
            // Treat an unreadable element as invalid input; callers reject empty vectors.
            env->ExceptionClear();
            env->DeleteLocalRef(element);
            return {};
        }
        result.emplace_back(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

// Coin control is security-relevant: an unreadable key-image list must not silently
// become "no exclusions". Returns false on any read failure; a null list is a valid empty
// selection.
bool java2cpp_set(JNIEnv *env, jobject arrayList, std::set<std::string> &result) {
    result.clear();
    if (arrayList == nullptr) return true;
    if (class_ArrayList == nullptr) return false;

    jmethodID java_util_ArrayList_size = env->GetMethodID(class_ArrayList, "size", "()I");
    if (java_util_ArrayList_size == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }
    jmethodID java_util_ArrayList_get = env->GetMethodID(class_ArrayList, "get",
                                                         "(I)Ljava/lang/Object;");
    if (java_util_ArrayList_get == nullptr || env->ExceptionCheck()) {
        env->ExceptionClear();
        return false;
    }

    jint len = env->CallIntMethod(arrayList, java_util_ArrayList_size);
    if (env->ExceptionCheck() || len < 0) {
        env->ExceptionClear();
        return false;
    }
    for (jint i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->CallObjectMethod(arrayList,
                                                                     java_util_ArrayList_get, i));
        if (element == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return false;
        }
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        if (pchars == nullptr) {
            env->ExceptionClear();
            env->DeleteLocalRef(element);
            return false;
        }
        result.emplace(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return true;
}

/// end helpers

#ifdef __cplusplus
extern "C"
{
#endif


/* WalletManager */
// Creates a Polyseed wallet. `password` unlocks wallet2's keys/cache; `passpharse` (typo is
// part of the JNI signature) is the seed passphrase. They are intentionally different.
JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletJ(JNIEnv *env, jobject,
                                                  jstring path,
                                                  jstring password,
                                                  jstring passpharse,
                                                  jstring language,
                                                  jint networkType) {
    (void) language; // wallet2 API no longer takes a language for Polyseed creation
    if (path == nullptr || password == nullptr || passpharse == nullptr) return 0;
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    ScopedUtfChars passphraseChars(env, passpharse);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr ||
        passphraseChars.get() == nullptr) {
        return 0;
    }

    std::string seed_words;
    std::string err;
    bool _polyseedCreate = Monero::Wallet::createPolyseed(seed_words, err);
    if (!_polyseedCreate) {
        LOGE("createWalletJ(): polyseed generation failed");
        return 0;
    }

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    try {
        Monero::Wallet *wallet =
                Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                        std::string(pathChars.get()),
                        std::string(passwordChars.get()),
                        _networkType,
                        seed_words,
                        std::string(passphraseChars.get()), true);
        if (wallet == nullptr) {
            LOGE("createWalletJ(): wallet creation failed");
            return 0;
        }

        bool setupStatus = wallet->setupBackgroundSync(
                Monero::Wallet::BackgroundSync_ReusePassword,
                std::string(passwordChars.get()), {});
        if (setupStatus == true) {
            LOGD("createWalletJ(): setupBackgroundSync(): success!");
        } else {
            LOGD("createWalletJ(): setupBackgroundSync(): failure!");
        }
        return reinterpret_cast<jlong>(wallet);
    } catch (...) {
        LOGE("createWalletJ(): wallet creation failed");
        return 0;
    }
}

// Opens an existing wallet. `isViewOnly` selects wallet2's watch-only mode; the NERO flavor
// sets it from BuildConfig.
JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_openWalletJ(JNIEnv *env, jobject,
                                                jstring path, jstring password,
                                                jint networkType, jboolean viewOnly) {
    LOGD("openWalletJ(): start");
    if (path == nullptr || password == nullptr) return 0;
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr) return 0;

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(pathChars.get()),
                    std::string(passwordChars.get()),
                    _networkType);
    if (wallet == nullptr) {
        LOGE("openWalletJ(): wallet open failed");
        return 0;
    }

    if (!viewOnly) {
        // setup background sync
        bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                       std::string(passwordChars.get()), {});
        if (setupStatus == true) {
            LOGD("openWalletJ(): setupBackgroundSync(): success!");
        } else {
            LOGD("openWalletJ(): setupBackgroundSync(): failure!");
        }
    }
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletJ(JNIEnv *env, jobject,
                                                    jstring path, jstring password,
                                                    jstring mnemonic, jstring offset,
                                                    jint networkType,
                                                    jlong restoreHeight) {
    if (path == nullptr || password == nullptr || mnemonic == nullptr || offset == nullptr) return 0;
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    ScopedUtfChars mnemonicChars(env, mnemonic);
    ScopedUtfChars offsetChars(env, offset);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr ||
        mnemonicChars.get() == nullptr || offsetChars.get() == nullptr) {
        return 0;
    }

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                    std::string(pathChars.get()),
                    std::string(passwordChars.get()),
                    std::string(mnemonicChars.get()),
                    _networkType,
                    (uint64_t) restoreHeight,
                    1, // kdf_rounds
                    std::string(offsetChars.get()));
    if (wallet == nullptr) {
        LOGE("recoveryWalletJ(): wallet recovery failed");
        return 0;
    }

    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(passwordChars.get()), {});
    if (setupStatus == true) {
        LOGD("recoveryWalletJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("recoveryWalletJ(): setupBackgroundSync(): failure!");
    }
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletPolyseedJ(JNIEnv *env, jobject,
                                                            jstring path, jstring password,
                                                            jstring mnemonic, jstring offset,
                                                            jint networkType) {
    if (path == nullptr || password == nullptr || mnemonic == nullptr || offset == nullptr) return 0;
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    ScopedUtfChars mnemonicChars(env, mnemonic);
    ScopedUtfChars offsetChars(env, offset);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr ||
        mnemonicChars.get() == nullptr || offsetChars.get() == nullptr) {
        return 0;
    }

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                    std::string(pathChars.get()),
                    std::string(passwordChars.get()),
                    _networkType,
                    std::string(mnemonicChars.get()),
                    std::string(offsetChars.get()), false);
    if (wallet == nullptr) {
        LOGE("recoveryWalletPolyseedJ(): wallet recovery failed");
        return 0;
    }

    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(passwordChars.get()), {});
    if (setupStatus == true) {
        LOGD("recoveryWalletPolyseedJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("recoveryWalletPolyseedJ(): setupBackgroundSync(): failure!");
    }
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletFromKeysJ(JNIEnv *env, jobject,
                                                          jstring path, jstring password,
                                                          jstring language,
                                                          jint networkType,
                                                          jlong restoreHeight,
                                                          jstring addressString,
                                                          jstring viewKeyString,
                                                          jstring spendKeyString) {
    if (path == nullptr || password == nullptr || language == nullptr ||
        addressString == nullptr || viewKeyString == nullptr || spendKeyString == nullptr) {
        return 0;
    }
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    ScopedUtfChars languageChars(env, language);
    ScopedUtfChars addressChars(env, addressString);
    ScopedUtfChars viewKeyChars(env, viewKeyString);
    ScopedUtfChars spendKeyChars(env, spendKeyString);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr ||
        languageChars.get() == nullptr || addressChars.get() == nullptr ||
        viewKeyChars.get() == nullptr || spendKeyChars.get() == nullptr) {
        return 0;
    }

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromKeys(
                    std::string(pathChars.get()),
                    std::string(passwordChars.get()),
                    std::string(languageChars.get()),
                    _networkType,
                    (uint64_t) restoreHeight,
                    std::string(addressChars.get()),
                    std::string(viewKeyChars.get()),
                    std::string(spendKeyChars.get()));
    if (wallet == nullptr) {
        LOGE("createWalletFromKeysJ(): wallet creation failed");
        return 0;
    }

    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(passwordChars.get()), {});
    if (setupStatus == true) {
        LOGD("createWalletFromKeysJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("createWalletFromKeysJ(): setupBackgroundSync(): failure!");
    }
    return reinterpret_cast<jlong>(wallet);
}


// virtual void setSubaddressLookahead(uint32_t major, uint32_t minor) = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletFromDeviceJ(JNIEnv *env, jobject,
                                                            jstring path,
                                                            jstring password,
                                                            jint networkType,
                                                            jstring deviceName,
                                                            jlong restoreHeight,
                                                            jstring subaddressLookahead) {
    if (path == nullptr || password == nullptr || deviceName == nullptr ||
        subaddressLookahead == nullptr) {
        return 0;
    }
    ScopedUtfChars pathChars(env, path);
    ScopedUtfChars passwordChars(env, password);
    ScopedUtfChars deviceChars(env, deviceName);
    ScopedUtfChars lookaheadChars(env, subaddressLookahead);
    if (pathChars.get() == nullptr || passwordChars.get() == nullptr ||
        deviceChars.get() == nullptr || lookaheadChars.get() == nullptr) {
        return 0;
    }

    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromDevice(
                    std::string(pathChars.get()),
                    std::string(passwordChars.get()),
                    _networkType,
                    std::string(deviceChars.get()),
                    (uint64_t) restoreHeight,
                    std::string(lookaheadChars.get()));
    if (wallet == nullptr) {
        LOGE("createWalletFromDeviceJ(): wallet creation failed");
        return 0;
    }

    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(passwordChars.get()), {});
    if (setupStatus == true) {
        LOGD("createWalletFromDeviceJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("createWalletFromDeviceJ(): setupBackgroundSync(): failure!");
    }
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_walletExists(JNIEnv *env, jobject,
                                                 jstring path) {
    if (path == nullptr) return JNI_FALSE;
    ScopedUtfChars pathChars(env, path);
    if (pathChars.get() == nullptr) return JNI_FALSE;
    bool exists =
            Monero::WalletManagerFactory::getWalletManager()->walletExists(
                    std::string(pathChars.get()));
    return static_cast<jboolean>(exists);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_verifyWalletPassword(JNIEnv *env, jobject,
                                                         jstring keys_file_name,
                                                         jstring password,
                                                         jboolean watch_only) {
    if (keys_file_name == nullptr || password == nullptr) return JNI_FALSE;
    ScopedUtfChars keysChars(env, keys_file_name);
    ScopedUtfChars passwordChars(env, password);
    if (keysChars.get() == nullptr || passwordChars.get() == nullptr) return JNI_FALSE;
    bool passwordOk =
            Monero::WalletManagerFactory::getWalletManager()->verifyWalletPassword(
                    std::string(keysChars.get()), std::string(passwordChars.get()), watch_only);
    return static_cast<jboolean>(passwordOk);
}

//virtual int queryWalletHardware(const std::string &keys_file_name, const std::string &password) const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_WalletManager_queryWalletDeviceJ(JNIEnv *env, jobject,
                                                       jstring keys_file_name,
                                                       jstring password) {
    if (keys_file_name == nullptr || password == nullptr) return -1;
    ScopedUtfChars keysChars(env, keys_file_name);
    ScopedUtfChars passwordChars(env, password);
    if (keysChars.get() == nullptr || passwordChars.get() == nullptr) return -1;
    Monero::Wallet::Device device_type;
    bool ok = Monero::WalletManagerFactory::getWalletManager()->
            queryWalletDevice(device_type, std::string(keysChars.get()),
                              std::string(passwordChars.get()));
    if (ok)
        return static_cast<jint>(device_type);
    else
        return -1;
}

//TODO virtual bool checkPayment(const std::string &address, const std::string &txid, const std::string &txkey, const std::string &daemon_address, uint64_t &received, uint64_t &height, std::string &error) const = 0;

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_setDaemonAddressJ(JNIEnv *env, jobject,
                                                      jstring address) {
    if (address == nullptr) return;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return;
    LOGD("setDaemonAddressJ(): start");
    Monero::WalletManagerFactory::getWalletManager()->setDaemonAddress(
            std::string(addressChars.get()));
    LOGD("setDaemonAddressJ(): end");
}

// returns whether the daemon can be reached, and its version number
JNIEXPORT jint JNICALL
Java_io_anonero_model_WalletManager_getDaemonVersion(JNIEnv *,
                                                     jobject) {
    uint32_t version;
    bool isConnected =
            Monero::WalletManagerFactory::getWalletManager()->connected(&version);
    if (!isConnected) version = 0;
    return version;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockchainHeight(JNIEnv *, jobject) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockchainTargetHeight(JNIEnv *,
                                                              jobject) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainTargetHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getNetworkDifficulty(JNIEnv *, jobject) {
    return Monero::WalletManagerFactory::getWalletManager()->networkDifficulty();
}

JNIEXPORT jdouble JNICALL
Java_io_anonero_model_WalletManager_getMiningHashRate(JNIEnv *, jobject) {
    return Monero::WalletManagerFactory::getWalletManager()->miningHashRate();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockTarget(JNIEnv *, jobject) {
    return Monero::WalletManagerFactory::getWalletManager()->blockTarget();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_isMining(JNIEnv *, jobject) {
    return static_cast<jboolean>(Monero::WalletManagerFactory::getWalletManager()->isMining());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_startMining(JNIEnv *env, jobject,
                                                jstring address,
                                                jboolean background_mining,
                                                jboolean ignore_battery) {
    if (address == nullptr) return JNI_FALSE;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return JNI_FALSE;
    bool success =
            Monero::WalletManagerFactory::getWalletManager()->startMining(
                    std::string(addressChars.get()), background_mining, ignore_battery);
    return static_cast<jboolean>(success);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_stopMining(JNIEnv *, jobject) {
    return static_cast<jboolean>(Monero::WalletManagerFactory::getWalletManager()->stopMining());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_WalletManager_resolveOpenAlias(JNIEnv *env, jobject,
                                                     jstring address,
                                                     jboolean dnssec_valid) {
    if (address == nullptr) return nullptr;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return nullptr;
    bool _dnssec_valid = (bool) dnssec_valid;
    std::string resolvedAlias =
            Monero::WalletManagerFactory::getWalletManager()->resolveOpenAlias(
                    std::string(addressChars.get()), _dnssec_valid);
    return env->NewStringUTF(resolvedAlias.c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_setProxyJ(JNIEnv *env, jobject,
                                              jstring address) {
    if (address == nullptr) return JNI_FALSE;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return JNI_FALSE;
    bool rc =
            Monero::WalletManagerFactory::getWalletManager()->setProxy(
                    std::string(addressChars.get()));
    return rc;
}


//TODO static std::tuple<bool, std::string, std::string, std::string, std::string> checkUpdates(const std::string &software, const std::string &subdir);

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_closeJ(JNIEnv *env, jobject,
                                           jobject walletInstance) {
    if (walletInstance == nullptr) return JNI_FALSE;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, walletInstance);
    if (wallet == nullptr) return JNI_FALSE;
    bool closeSuccess = Monero::WalletManagerFactory::getWalletManager()->closeWallet(wallet,
                                                                                      false);
    if (closeSuccess) {
        // Invalidate the raw handle before the Kotlin wrapper can be used again.
        setHandleFromLong(env, walletInstance, 0);
        MyWalletListener *walletListener = getHandle<MyWalletListener>(env, walletInstance,
                                                                       "listenerHandle");
        if (walletListener != nullptr) {
            walletListener->deleteGlobalJavaRef(env);
            delete walletListener;
        }
        // The Kotlin wrapper must not retain a pointer to the deleted listener.
        jclass walletClass = env->GetObjectClass(walletInstance);
        if (walletClass != nullptr) {
            jfieldID listenerField = env->GetFieldID(walletClass, "listenerHandle", "J");
            if (listenerField != nullptr) {
                env->SetLongField(walletInstance, listenerField, 0);
            }
            env->DeleteLocalRef(walletClass);
            if (env->ExceptionCheck()) env->ExceptionClear();
        }
        LOGD("wallet closed");
    } else {
        LOGE("wallet close failed");
    }
    return static_cast<jboolean>(closeSuccess);
}




/* Wallet */

// For backwards compatibility reasons, getSeed will continue silently fallback to legacy mnemonic
// seed.
// Since the changes aren't upstremead yet, and we don't know how polyseed will evolve / if there
// will be any changes we wouldn't like to have the users stuck with not working wallet that they
// can't backup.
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSeed(JNIEnv *env, jobject instance, jstring seedOffset) {
    std::string offset;
    if (seedOffset != nullptr) {
        ScopedUtfChars offsetChars(env, seedOffset);
        if (offsetChars.get() == nullptr) return nullptr;
        offset = offsetChars.get();
    }
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return nullptr;

    std::string _seed;
    bool _getPolyseed = wallet->getPolyseed(_seed, offset);

    if (_getPolyseed) {
        LOGD("_getPolyseed: true");
    } else {
        LOGD("_getPolyseed: false");
        LOGE("wallet->getSeed() %s", wallet->errorString().c_str());
        LOGD("Falling back to normal, boring, seed");
        _seed = wallet->seed(offset);
    }
    return env->NewStringUTF(_seed.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getLegacySeed(JNIEnv *env, jobject instance, jstring seedOffset) {
    std::string offset;
    if (seedOffset != nullptr) {
        ScopedUtfChars offsetChars(env, seedOffset);
        if (offsetChars.get() == nullptr) return nullptr;
        offset = offsetChars.get();
    }
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return nullptr;
    std::string seed = wallet->seed(offset);
    return env->NewStringUTF(seed.c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isPolyseedSupported(JNIEnv *env, jobject instance,
                                                 jstring seedOffset) {
    std::string offset;
    if (seedOffset != nullptr) {
        ScopedUtfChars offsetChars(env, seedOffset);
        if (offsetChars.get() == nullptr) return JNI_FALSE;
        offset = offsetChars.get();
    }
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    std::string seed;
    return static_cast<jboolean>(wallet->getPolyseed(seed, offset));
}


JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSeedLanguage(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(wallet->getSeedLanguage().c_str());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setSeedLanguage(JNIEnv *env, jobject instance,
                                             jstring language) {
    if (language == nullptr) return;
    ScopedUtfChars languageChars(env, language);
    if (languageChars.get() == nullptr) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    wallet->setSeedLanguage(std::string(languageChars.get()));
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return static_cast<jint>(Monero::Wallet::Status_Critical);
    return wallet->status();
}

jobject newWalletStatusInstance(JNIEnv *env, int status, const std::string &errorString) {
    jmethodID init = env->GetMethodID(class_WalletStatus, "<init>",
                                      "(ILjava/lang/String;)V");
    if (init == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jstring _errorString = env->NewStringUTF(errorString.c_str());
    jobject instance = env->NewObject(class_WalletStatus, init, status, _errorString);
    env->DeleteLocalRef(_errorString);
    if (env->ExceptionCheck()) env->ExceptionClear();
    return instance;
}


JNIEXPORT jobject JNICALL
Java_io_anonero_model_Wallet_statusWithErrorString(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) {
        return newWalletStatusInstance(env, 2, "wallet unavailable");
    }

    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);

    return newWalletStatusInstance(env, status, errorString);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setPassword(JNIEnv *env, jobject instance,
                                         jstring password) {
    if (password == nullptr) return JNI_FALSE;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    ScopedUtfChars passwordChars(env, password);
    if (passwordChars.get() == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(wallet->setPassword(std::string(passwordChars.get())));
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getAddressJ(JNIEnv *env, jobject instance,
                                         jint accountIndex,
                                         jint addressIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || accountIndex < 0 || addressIndex < 0) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(
            wallet->address(static_cast<uint32_t>(accountIndex),
                            static_cast<uint32_t>(addressIndex)).c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getPath(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(wallet->path().c_str());
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_nettype(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return wallet->nettype();
}

//TODO virtual void hardForkInfo(uint8_t &version, uint64_t &earliest_height) const = 0;
//TODO virtual bool useForkRules(uint8_t version, int64_t early_blocks) const = 0;

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getIntegratedAddress(JNIEnv *env, jobject instance,
                                                  jstring payment_id) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || payment_id == nullptr) return env->NewStringUTF("");
    ScopedUtfChars paymentChars(env, payment_id);
    if (paymentChars.get() == nullptr) return env->NewStringUTF("");
    std::string address = wallet->integratedAddress(paymentChars.get());
    return env->NewStringUTF(address.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSecretViewKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return nullptr;
    return env->NewStringUTF(wallet->secretViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSecretSpendKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return nullptr;
    return env->NewStringUTF(wallet->secretSpendKey().c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_store(JNIEnv *env, jobject instance,
                                   jstring path) {
    if (path == nullptr) return JNI_FALSE;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    ScopedUtfChars pathChars(env, path);
    if (pathChars.get() == nullptr) return JNI_FALSE;
    bool success = wallet->store(std::string(pathChars.get()));
    if (!success) {
        LOGE("store(): %s", wallet->errorString().c_str());
    }
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getFilename(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(wallet->filename().c_str());
}

//    virtual std::string keysFilename() const = 0;

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_initJ(JNIEnv *env, jobject instance,
                                   jstring daemon_address,
                                   jlong upper_transaction_size_limit,
                                   jstring daemon_username, jstring daemon_password,
                                   jstring proxy_address, jboolean light_wallet) {
    if (daemon_address == nullptr || daemon_username == nullptr ||
        daemon_password == nullptr || proxy_address == nullptr) {
        return JNI_FALSE;
    }
    ScopedUtfChars daemonChars(env, daemon_address);
    ScopedUtfChars usernameChars(env, daemon_username);
    ScopedUtfChars passwordChars(env, daemon_password);
    ScopedUtfChars proxyChars(env, proxy_address);
    if (daemonChars.get() == nullptr || usernameChars.get() == nullptr ||
        passwordChars.get() == nullptr || proxyChars.get() == nullptr) {
        return JNI_FALSE;
    }
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    LOGD("initJ(): start");
    bool status = wallet->init(daemonChars.get(), (uint64_t) upper_transaction_size_limit,
                               usernameChars.get(),
                               passwordChars.get(),
                               false,
                               static_cast<bool>(light_wallet),
                               proxyChars.get());
    LOGD("initJ(): end");
    return static_cast<jboolean>(status);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_lightWalletLogin(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    bool isNewWallet = false;
    bool ok;
    try {
        ok = wallet->lightWalletLogin(isNewWallet);
    } catch (const std::exception &e) {
        LOGE("lightWalletLogin: %s", e.what());
        ok = false;
    }
    if (!ok) {
        LOGE("lightWalletLogin failed: %s", wallet->errorString().c_str());
    }
    return static_cast<jboolean>(ok);
}

//    virtual bool createWatchOnly(const std::string &path, const std::string &password, const std::string &language) const = 0;

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setRestoreHeight(JNIEnv *env, jobject instance,
                                              jlong height) {
    if (height < 0) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    wallet->setRefreshFromBlockHeight(static_cast<uint64_t>(height));
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getRestoreHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->getRefreshFromBlockHeight());
}

//    virtual void setRecoveringFromSeed(bool recoveringFromSeed) = 0;
//    virtual bool connectToDaemon() = 0;

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getConnectionStatusJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jint>(wallet->connected());
}
//TODO virtual void setTrustedDaemon(bool arg) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setTrustedDaemon(JNIEnv *env, jobject instance, jboolean arg) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    wallet->setTrustedDaemon(arg);
    return JNI_TRUE;
}

//virtual void setOffline(bool offline) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setOffline(JNIEnv *env, jobject instance, jboolean offline) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet != nullptr) wallet->setOffline(offline);
}


// TODO virtual bool trustedDaemon() const = 0;

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setProxyJ(JNIEnv *env, jobject instance,
                                       jstring address) {
    if (address == nullptr) return JNI_FALSE;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(wallet->setProxy(std::string(addressChars.get())));
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBalance(JNIEnv *env, jobject instance,
                                        jint accountIndex) {
    if (accountIndex < 0) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->balance((uint32_t) accountIndex));
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBalanceAll(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->balanceAll());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getUnlockedBalance(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    if (accountIndex < 0) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0
                             : static_cast<jlong>(wallet->unlockedBalance((uint32_t) accountIndex));
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getUnlockedBalanceAll(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->unlockedBalanceAll());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isWatchOnly(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet != nullptr && wallet->watchOnly());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBlockChainHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->blockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getApproximateBlockChainHeight(JNIEnv *env,
                                                            jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->approximateBlockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getDaemonBlockChainHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->daemonBlockChainHeight());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getDaemonBlockChainTargetHeight(JNIEnv *env,
                                                             jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jlong>(wallet->daemonBlockChainTargetHeight());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isSynchronizedJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet != nullptr && wallet->synchronized());
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getDeviceTypeJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    Monero::Wallet::Device device_type = wallet->getDeviceType();
    return static_cast<jint>(device_type);
}

//void cn_slow_hash(const void *data, size_t length, char *hash); // from crypto/hash-ops.h
JNIEXPORT jbyteArray JNICALL
Java_io_anonero_util_KeyStoreHelper_slowHash(JNIEnv *env, jclass,
                                             jbyteArray data, jint brokenVariant) {
    if (data == nullptr) return nullptr;
    char hash[HASH_SIZE];
    jsize size = env->GetArrayLength(data);
    if ((brokenVariant > 0) && (size < 200 /*sizeof(union hash_state)*/)) {
        return nullptr;
    }

    jbyte *buffer = env->GetByteArrayElements(data, nullptr);
    if (buffer == nullptr) return nullptr;
    switch (brokenVariant) {
        case 1:
            slow_hash_broken(buffer, hash, 1);
            break;
        case 2:
            slow_hash_broken(buffer, hash, 0);
            break;
        default: // not broken
            slow_hash(buffer, (size_t) size, hash);
    }
    env->ReleaseByteArrayElements(data, buffer, JNI_ABORT); // do not update java byte[]
    jbyteArray result = env->NewByteArray(HASH_SIZE);
    if (result == nullptr) return nullptr;
    env->SetByteArrayRegion(result, 0, HASH_SIZE, (jbyte *) hash);
    return result;
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getDisplayAmount(JNIEnv *env, jclass,
                                              jlong amount) {
    return env->NewStringUTF(Monero::Wallet::displayAmount(amount).c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_generatePaymentId(JNIEnv *env, jclass) {
    return env->NewStringUTF(Monero::Wallet::genPaymentId().c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isPaymentIdValid(JNIEnv *env, jclass,
                                              jstring payment_id) {
    if (payment_id == nullptr) return JNI_FALSE;
    ScopedUtfChars paymentChars(env, payment_id);
    if (paymentChars.get() == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(Monero::Wallet::paymentIdValid(paymentChars.get()));
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isAddressValid(JNIEnv *env, jclass,
                                            jstring address, jint networkType) {
    if (address == nullptr || networkType < 0 || networkType > 3) return JNI_FALSE;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(Monero::Wallet::addressValid(
            addressChars.get(), static_cast<Monero::NetworkType>(networkType)));
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getPaymentIdFromAddress(JNIEnv *env, jclass,
                                                     jstring address,
                                                     jint networkType) {
    if (address == nullptr || networkType < 0 || networkType > 3) return nullptr;
    ScopedUtfChars addressChars(env, address);
    if (addressChars.get() == nullptr) return nullptr;
    std::string payment_id = Monero::Wallet::paymentIdFromAddress(
            addressChars.get(), static_cast<Monero::NetworkType>(networkType));
    return env->NewStringUTF(payment_id.c_str());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getMaximumAllowedAmount(JNIEnv *env, jclass) {
    (void) env;
    return Monero::Wallet::maximumAllowedAmount();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_startRefresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet != nullptr) wallet->startRefresh();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_pauseRefresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet != nullptr) wallet->pauseRefresh();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_startBackgroundSync(JNIEnv *env, jobject instance) {
    LOGD("startBackgroundSync(): start");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;

    bool bgsyncStatus = wallet->startBackgroundSync();
    if (bgsyncStatus == true) {
        LOGD("startBackgroundSync(): end: success!");
    } else {
        LOGD("startBackgroundSync(): end: fail!");
    }
    return static_cast<jboolean>(bgsyncStatus);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_stopBackgroundSync(JNIEnv *env, jobject instance, jstring password) {
    LOGD("stopBackgroundSync(): start");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || password == nullptr) return JNI_FALSE;
    ScopedUtfChars passwordChars(env, password);
    if (passwordChars.get() == nullptr) return JNI_FALSE;
    bool bgsyncStatus = wallet->stopBackgroundSync(std::string(passwordChars.get()));
    if (bgsyncStatus == true) {
        LOGD("stopBackgroundSync(): end: success!");
    } else {
        LOGD("stopBackgroundSync(): end: fail!");
    }
    return static_cast<jboolean>(bgsyncStatus);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_refresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(wallet->refresh());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_refreshAsync(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet != nullptr) wallet->refreshAsync();
}

//TODO virtual bool rescanBlockchain() = 0;

//virtual void rescanBlockchainAsync() = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_rescanBlockchainAsyncJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet != nullptr) wallet->rescanBlockchainAsync();
}


//TODO virtual void setAutoRefreshInterval(int millis) = 0;
//TODO virtual int autoRefreshInterval() const = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createTransactionJ(JNIEnv *env, jobject instance,
                                                jstring dst_addr, jstring payment_id,
                                                jlong amount, jint mixin_count,
                                                jint priority,
                                                jint accountIndex, jobject key_images) {
    if (dst_addr == nullptr || payment_id == nullptr) return 0;
    if (amount <= 0 || mixin_count < 0 || accountIndex < 0 || priority < 0 || priority > 3) {
        return 0;
    }
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;

    ScopedUtfChars dstChars(env, dst_addr);
    ScopedUtfChars paymentChars(env, payment_id);
    if (dstChars.get() == nullptr || paymentChars.get() == nullptr) return 0;

    std::set<std::string> _key_images;
    if (!java2cpp_set(env, key_images, _key_images)) {
        LOGE("createTransactionJ: key images unavailable");
        return 0;
    }
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    try {
        Monero::PendingTransaction *tx = wallet->createTransaction(
                dstChars.get(), paymentChars.get(),
                static_cast<uint64_t>(amount), static_cast<uint32_t>(mixin_count),
                _priority,
                static_cast<uint32_t>(accountIndex), {},
                _key_images);
        return reinterpret_cast<jlong>(tx);
    } catch (...) {
        // Do not log wallet2's text: it can contain address or amount fragments.
        LOGE("createTransactionJ failed");
        return 0;
    }
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createTransactionMultDestJ(JNIEnv *env, jobject instance,
                                                       jobjectArray dst_addr,
                                                       jlongArray amounts,
                                                       jint mixin_count, jint priority,
                                                       jint accountIndex, jobject key_images) {
    if (mixin_count < 0 || accountIndex < 0 || priority < 0 || priority > 3) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    std::vector<std::string> destinations = java2cpp_string_array(env, dst_addr);
    std::vector<uint64_t> amount_vector = java2cpp_long(env, amounts);
    if (destinations.empty() || destinations.size() != amount_vector.size()) {
        LOGE("createTransactionMultDestJ: destination/amount count mismatch");
        return 0;
    }
    for (const auto &destination : destinations) {
        if (destination.empty()) return 0;
    }
    try {
        std::set<std::string> _key_images;
        if (!java2cpp_set(env, key_images, _key_images)) {
            LOGE("createTransactionMultDestJ: key images unavailable");
            return 0;
        }
        Monero::PendingTransaction::Priority _priority =
                static_cast<Monero::PendingTransaction::Priority>(priority);
        Monero::optional<std::vector<uint64_t>> amount_opt(amount_vector);
        Monero::PendingTransaction *tx = wallet->createTransactionMultDest(
                destinations, "", amount_opt, static_cast<uint32_t>(mixin_count), _priority,
                static_cast<uint32_t>(accountIndex), {}, _key_images);
        return reinterpret_cast<jlong>(tx);
    } catch (...) {
        LOGE("createTransactionMultDestJ failed");
        return 0;
    }
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_estimateTransactionFeeJ(JNIEnv *env, jobject instance,
                                                     jobjectArray destinations,
                                                     jlongArray amounts, jint priority) {
    if (priority < 0 || priority > 3) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    std::vector<std::string> addresses = java2cpp_string_array(env, destinations);
    std::vector<uint64_t> amount_vector = java2cpp_long(env, amounts);
    if (addresses.empty() || addresses.size() != amount_vector.size()) {
        LOGE("estimateTransactionFeeJ: destination/amount count mismatch");
        return 0;
    }
    std::vector<std::pair<std::string, uint64_t>> dest_vector;
    dest_vector.reserve(addresses.size());
    for (size_t i = 0; i < addresses.size(); ++i) {
        if (addresses[i].empty()) return 0;
        dest_vector.emplace_back(addresses[i], amount_vector[i]);
    }
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    try {
        return static_cast<jlong>(wallet->estimateTransactionFee(dest_vector, _priority));
    } catch (...) {
        LOGE("estimateTransactionFeeJ failed");
        return 0;
    }
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createSweepTransaction(JNIEnv *env, jobject instance,
                                                    jstring dst_addr, jstring payment_id,
                                                    jint mixin_count,
                                                    jint priority,
                                                    jint accountIndex,
                                                    jobject key_images) {
    if (dst_addr == nullptr || payment_id == nullptr) return 0;
    if (mixin_count < 0 || accountIndex < 0 || priority < 0 || priority > 3) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;

    ScopedUtfChars dstChars(env, dst_addr);
    ScopedUtfChars paymentChars(env, payment_id);
    if (dstChars.get() == nullptr || paymentChars.get() == nullptr) return 0;

    try {
        std::set<std::string> _key_images;
        if (!java2cpp_set(env, key_images, _key_images)) {
            LOGE("createSweepTransaction: key images unavailable");
            return 0;
        }
        Monero::PendingTransaction::Priority _priority =
                static_cast<Monero::PendingTransaction::Priority>(priority);
        Monero::optional<uint64_t> empty;
        Monero::PendingTransaction *tx = wallet->createTransaction(
                dstChars.get(), paymentChars.get(), empty,
                static_cast<uint32_t>(mixin_count), _priority,
                static_cast<uint32_t>(accountIndex), {}, _key_images);
        return reinterpret_cast<jlong>(tx);
    } catch (...) {
        LOGE("createSweepTransaction failed");
        return 0;
    }
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createSweepUnmixableTransactionJ(JNIEnv *env,
                                                              jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    try {
        Monero::PendingTransaction *tx = wallet->createSweepUnmixableTransaction();
        return reinterpret_cast<jlong>(tx);
    } catch (...) {
        LOGE("createSweepUnmixableTransactionJ failed");
        return 0;
    }
}

//virtual UnsignedTransaction * loadUnsignedTx(const std::string &unsigned_filename) = 0;
//virtual bool submitTransaction(const std::string &fileName) = 0;

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_disposeTransaction(JNIEnv *env, jobject instance,
                                                jobject pendingTransaction) {
    if (pendingTransaction == nullptr) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    Monero::PendingTransaction *_pendingTransaction =
            getHandle<Monero::PendingTransaction>(env, pendingTransaction);
    if (_pendingTransaction != nullptr) wallet->disposeTransaction(_pendingTransaction);
}

//virtual bool exportKeyIexportKeyImagesmages(const std::string &filename, bool all = false) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_exportKeyImages(JNIEnv *env, jobject instance, jstring filename,
                                             jboolean all) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || filename == nullptr) return JNI_FALSE;
    ScopedUtfChars filenameChars(env, filename);
    if (filenameChars.get() == nullptr) return JNI_FALSE;
    bool result = false;
    try {
        result = wallet->exportKeyImages(filenameChars.get(), all);
    } catch (const std::exception &e) {
        LOGE("exportKeyImages: %s", e.what());
    }
    return static_cast<jboolean>(result);
}


//virtual bool importKeyImages(const std::string &filename) = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_importKeyImages(JNIEnv *env, jobject instance, jstring filename) {
    static std::atomic<bool> isImportKeyImagesLocked{false};
    bool expected = false;
    if (!isImportKeyImagesLocked.compare_exchange_strong(expected, true)) {
        LOGD("monerujo.cpp: importKeyImages: already in progress");
        return env->NewStringUTF("Import request ignored. This is a Bug. Please report it.");
    }
    ScopedAtomicFlagReset resetImportLock(isImportKeyImagesLocked);
    if (filename == nullptr) {
        return env->NewStringUTF("Import file is missing");
    }
    ScopedUtfChars inputFile(env, filename);
    if (inputFile.get() == nullptr) {
        return env->ExceptionCheck() ? nullptr : env->NewStringUTF("Import file is unavailable");
    }
    try {
        Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
        if (wallet == nullptr) return env->NewStringUTF("Wallet is unavailable");
        const bool success = wallet->importKeyImages(inputFile.get());
        return env->NewStringUTF(success ? "Imported" : wallet->errorString().c_str());
    } catch (const std::exception &e) {
        return env->NewStringUTF(e.what());
    } catch (...) {
        return env->NewStringUTF("Import file is unavailable");
    }
}


// virtual bool submitTransaction(const std::string &fileName) = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_submitTransaction(JNIEnv *env, jobject instance, jstring filename) {
    if (filename == nullptr) return env->NewStringUTF("Transaction file is missing");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("Wallet is unavailable");
    ScopedUtfChars filenameChars(env, filename);
    if (filenameChars.get() == nullptr) {
        return env->NewStringUTF("Transaction file is unavailable");
    }
    bool success = false;
    std::string error;
    try {
        success = wallet->submitTransaction(filenameChars.get());
        if (!success) error = wallet->errorString();
    } catch (const std::exception &e) {
        error = e.what();
    }
    return env->NewStringUTF(success ? "Transaction submitted!" : error.c_str());
}

//virtual bool exportOutputs(const std::string &filename, bool all = false) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_exportOutputs(JNIEnv *env, jobject instance, jstring filename,
                                           jboolean all) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || filename == nullptr) return JNI_FALSE;
    ScopedUtfChars filenameChars(env, filename);
    if (filenameChars.get() == nullptr) return JNI_FALSE;
    bool result = false;
    try {
        result = wallet->exportOutputs(filenameChars.get(), all);
    } catch (const std::exception &e) {
        LOGE("exportOutputs: %s", e.what());
    }
    return static_cast<jboolean>(result);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_hasUnknownKeyImages(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    return static_cast<jboolean>(wallet->hasUnknownKeyImages());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_viewOnlyBalance(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return static_cast<jlong>(wallet->viewOnlyBalance(0)); // legacy default account
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_viewOnlyBalanceFor(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    if (accountIndex < 0) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return static_cast<jlong>(wallet->viewOnlyBalance(static_cast<uint32_t>(accountIndex)));
}

//virtual bool importOutputs(const std::string &filename) = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_importOutputs(JNIEnv *env, jobject instance, jstring filename) {
    if (filename == nullptr) return env->NewStringUTF("Outputs file is missing");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("Wallet is unavailable");
    ScopedUtfChars filenameChars(env, filename);
    if (filenameChars.get() == nullptr) {
        return env->NewStringUTF("Outputs file is unavailable");
    }
    bool success = false;
    std::string error;
    try {
        success = wallet->importOutputs(filenameChars.get());
        if (!success) error = wallet->errorString();
    } catch (const std::exception &e) {
        error = e.what();
    }
    return env->NewStringUTF(success ? "Imported" : error.c_str());
}

// Reloads the unsigned file from disk and signs whatever is there now. The reviewed-object
// path is UnsignedTransaction.signJ, which signs the object the user actually saw.
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_signAndExportJ(JNIEnv *env, jobject instance, jstring input_file,
                                            jstring output_file) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) {
        return env->NewStringUTF("Wallet is unavailable");
    }
    if (input_file == nullptr || output_file == nullptr) {
        return env->NewStringUTF("Unsigned/signed transaction path is missing");
    }
    ScopedUtfChars inputChars(env, input_file);
    ScopedUtfChars outputChars(env, output_file);
    if (inputChars.get() == nullptr || outputChars.get() == nullptr) {
        return env->NewStringUTF("Transaction path is unavailable");
    }
    bool success = false;
    std::string error;
    try {
        std::unique_ptr<Monero::UnsignedTransaction> tx(
                wallet->loadUnsignedTx(inputChars.get()));
        if (!tx) {
            error = "Could not load unsigned transaction";
        } else {
            success = tx->sign(outputChars.get());
            if (!success) error = wallet->errorString();
        }
    } catch (const std::exception &e) {
        error = e.what();
    }
    return env->NewStringUTF(success ? "Signed" : error.c_str());
}
JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_loadUnsignedTx(JNIEnv *env, jobject instance, jstring input_file) {
    LOGD("monerujo.cpp: loadUnsignedTx: start");
    if (input_file == nullptr) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    ScopedUtfChars inputChars(env, input_file);
    if (inputChars.get() == nullptr) return 0;
    Monero::UnsignedTransaction *tx = nullptr;
    try {
        tx = wallet->loadUnsignedTx(inputChars.get());
    } catch (...) {
        LOGE("loadUnsignedTx: native load failed");
    }
    LOGD("monerujo.cpp: loadUnsignedTx: end");
    return reinterpret_cast<jlong>(tx);
}

//virtual TransactionHistory * history() const = 0;
JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getHistoryJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return reinterpret_cast<jlong>(wallet->history());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getCoinsJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return reinterpret_cast<jlong>(wallet->coins());
}


jobject newCoinsInfo(JNIEnv *env, Monero::CoinsInfo *info) {
    if (info == nullptr) return nullptr;
    // Keep this signature in sync with io.anonero.model.CoinsInfo's primary constructor.
    jmethodID c = env->GetMethodID(class_CoinsInfo, "<init>",
                                   "(JZLjava/lang/String;JLjava/lang/String;Ljava/lang/String;ZJZIIJLjava/lang/String;Ljava/lang/String;Z)V");
    if (c == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jstring _key_image = env->NewStringUTF(info->keyImage().c_str());
    jstring _pub_key = env->NewStringUTF(info->pubKey().c_str());
    jstring _hash = env->NewStringUTF(info->hash().c_str());
    jstring _address = env->NewStringUTF(info->address().c_str());
    jstring _address_label = env->NewStringUTF(info->addressLabel().c_str());
    jobject result = env->NewObject(class_CoinsInfo, c,
                                    static_cast<jlong> (info->globalOutputIndex()),
                                    info->spent(),
                                    _key_image,
                                    static_cast<jlong> (info->amount()),
                                    _hash,
                                    _pub_key,
                                    info->frozen(),
                                    static_cast<jlong> (info->internalOutputIndex()),
                                    info->unlocked(),
                                    static_cast<jint> (info->subaddrAccount()),
                                    static_cast<jint> (info->subaddrIndex()),
                                    static_cast<jlong> (info->blockHeight()),
                                    _address,
                                    _address_label,
                                    info->keyImageKnown());
    env->DeleteLocalRef(_key_image);
    env->DeleteLocalRef(_hash);
    env->DeleteLocalRef(_pub_key);
    env->DeleteLocalRef(_address);
    env->DeleteLocalRef(_address_label);
    if (env->ExceptionCheck()) env->ExceptionClear();
    return result;
}

jobject coins_cpp2java(JNIEnv *env, const std::vector<Monero::CoinsInfo *> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");
    if (java_util_ArrayList_ == nullptr || java_util_ArrayList_add == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                       static_cast<jint> (vector.size()));
    if (arrayList == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    for (Monero::CoinsInfo *s: vector) {
        jobject info = newCoinsInfo(env, s);
        if (info == nullptr) continue;
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    return arrayList;
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Coins_getCount(JNIEnv *env, jobject instance) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    return coins == nullptr ? 0 : coins->count();
}

JNIEXPORT jobject JNICALL
Java_io_anonero_model_Coins_refreshJ(JNIEnv *env, jobject instance) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    if (coins == nullptr) return nullptr;
    coins->refresh();
    return coins_cpp2java(env, coins->getAll());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Coins_setFrozenJ(JNIEnv *env, jobject instance, jstring public_key) {
    if (public_key == nullptr) return;
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    if (coins == nullptr) return;
    ScopedUtfChars keyChars(env, public_key);
    if (keyChars.get() == nullptr) return;
    try {
        coins->setFrozen(std::string(keyChars.get()));
    } catch (const std::exception &e) {
        LOGE("Coins.setFrozen: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Coins_thawJ(JNIEnv *env, jobject instance, jstring public_key) {
    if (public_key == nullptr) return;
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    if (coins == nullptr) return;
    ScopedUtfChars keyChars(env, public_key);
    if (keyChars.get() == nullptr) return;
    try {
        coins->thaw(std::string(keyChars.get()));
    } catch (const std::exception &e) {
        LOGE("Coins.thaw: %s", e.what());
    }
}


//virtual AddressBook * addressBook() const = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_setListenerJ(JNIEnv *env, jobject instance,
                                          jobject javaListener) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    // Delete the old listener before any early return: returning 0 from here would
    // otherwise lose the only pointer to the global Java reference.
    MyWalletListener *oldListener = getHandle<MyWalletListener>(env, instance,
                                                                "listenerHandle");
    if (oldListener != nullptr) {
        oldListener->deleteGlobalJavaRef(env);
        delete oldListener;
    }
    if (wallet == nullptr) return 0;
    wallet->setListener(nullptr); // clear old listener
    if (javaListener == nullptr) {
        LOGD("null listener");
        return 0;
    }
    MyWalletListener *listener = new (std::nothrow) MyWalletListener(env, javaListener);
    if (listener == nullptr || listener->jlistener == nullptr) {
        // NewGlobalRef failed (OOM or a pending JNI exception). Do not install a listener
        // that would silently drop every callback.
        delete listener;
        return 0;
    }
    try {
        wallet->setListener(listener);
    } catch (...) {
        listener->deleteGlobalJavaRef(env);
        delete listener;
        return 0;
    }
    return reinterpret_cast<jlong>(listener);
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getDefaultMixin(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : wallet->defaultMixin();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setDefaultMixin(JNIEnv *env, jobject instance, jint mixin) {
    if (mixin < 0) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    wallet->setDefaultMixin(mixin);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setUserNote(JNIEnv *env, jobject instance,
                                         jstring txid, jstring note) {
    if (txid == nullptr || note == nullptr) return JNI_FALSE;
    ScopedUtfChars txidChars(env, txid);
    ScopedUtfChars noteChars(env, note);
    if (txidChars.get() == nullptr || noteChars.get() == nullptr) return JNI_FALSE;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    bool success = wallet->setUserNote(txidChars.get(), noteChars.get());
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getUserNote(JNIEnv *env, jobject instance,
                                         jstring txid) {
    if (txid == nullptr) return env->NewStringUTF("");
    ScopedUtfChars txidChars(env, txid);
    if (txidChars.get() == nullptr) return env->NewStringUTF("");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    std::string note = wallet->getUserNote(txidChars.get());
    return env->NewStringUTF(note.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getTxKey(JNIEnv *env, jobject instance,
                                      jstring txid) {
    if (txid == nullptr) return env->NewStringUTF("");
    ScopedUtfChars txidChars(env, txid);
    if (txidChars.get() == nullptr) return env->NewStringUTF("");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    std::string txKey = wallet->getTxKey(txidChars.get());
    return env->NewStringUTF(txKey.c_str());
}

//virtual void addSubaddressAccount(const std::string& label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_addAccount(JNIEnv *env, jobject instance,
                                        jstring label) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    if (label == nullptr) {
        wallet->addSubaddressAccount("");
        return;
    }
    ScopedUtfChars labelChars(env, label);
    if (labelChars.get() != nullptr) {
        wallet->addSubaddressAccount(labelChars.get());
    }
}

//virtual std::string getSubaddressLabel(uint32_t accountIndex, uint32_t addressIndex) const = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSubaddressLabel(JNIEnv *env, jobject instance,
                                                jint accountIndex, jint addressIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr || accountIndex < 0 || addressIndex < 0) {
        return env->NewStringUTF("");
    }
    std::string label = wallet->getSubaddressLabel(static_cast<uint32_t>(accountIndex),
                                                   static_cast<uint32_t>(addressIndex));
    return env->NewStringUTF(label.c_str());
}

//virtual void setSubaddressLabel(uint32_t accountIndex, uint32_t addressIndex, const std::string &label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setSubaddressLabel(JNIEnv *env, jobject instance,
                                                jint accountIndex, jint addressIndex,
                                                jstring label) {
    if (accountIndex < 0 || addressIndex < 0) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    if (label == nullptr) {
        wallet->setSubaddressLabel(static_cast<uint32_t>(accountIndex),
                                   static_cast<uint32_t>(addressIndex), "");
        return;
    }
    ScopedUtfChars labelChars(env, label);
    if (labelChars.get() != nullptr) {
        wallet->setSubaddressLabel(static_cast<uint32_t>(accountIndex),
                                   static_cast<uint32_t>(addressIndex), labelChars.get());
    }
}

// virtual size_t numSubaddressAccounts() const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getNumAccounts(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet == nullptr ? 0 : static_cast<jint>(wallet->numSubaddressAccounts());
}

//virtual size_t numSubaddresses(uint32_t accountIndex) const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getNumSubaddresses(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    if (accountIndex < 0) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return 0;
    return static_cast<jint>(wallet->numSubaddresses(static_cast<uint32_t>(accountIndex)));
}

//virtual void addSubaddress(uint32_t accountIndex, const std::string &label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_addSubaddress(JNIEnv *env, jobject instance,
                                           jint accountIndex,
                                           jstring label) {
    if (accountIndex < 0) return;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return;
    if (label == nullptr) {
        wallet->addSubaddress(static_cast<uint32_t>(accountIndex), "");
        return;
    }
    ScopedUtfChars labelChars(env, label);
    if (labelChars.get() != nullptr) {
        wallet->addSubaddress(static_cast<uint32_t>(accountIndex), labelChars.get());
    }
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_signMessageJ(JNIEnv *env, jobject instance, jbyteArray message) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return env->NewStringUTF("");
    std::string signature;
    try {
        signature = wallet->signMessage(java2cpp_bytes(env, message));
    } catch (const std::exception &e) {
        LOGE("signMessage: %s", e.what());
        signature.clear();
    }
    if (signature.empty() && wallet->status() != Monero::Wallet::Status_Ok) {
        LOGE("signMessage failed: %s", wallet->errorString().c_str());
    }
    return env->NewStringUTF(signature.c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_verifySignedMessageJ(JNIEnv *env, jobject instance,
                                                  jbyteArray message, jbyteArray address,
                                                  jbyteArray signature) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (wallet == nullptr) return JNI_FALSE;
    bool valid = false;
    try {
        valid = wallet->verifySignedMessage(
                java2cpp_bytes(env, message),
                java2cpp_bytes(env, address),
                java2cpp_bytes(env, signature));
    } catch (const std::exception &e) {
        LOGE("verifySignedMessage: %s", e.what());
        valid = false;
    }
    return static_cast<jboolean>(valid);
}

//virtual bool parse_uri(const std::string &uri, std::string &address, std::string &payment_id, uint64_t &tvAmount, std::string &tx_description, std::string &recipient_name, std::vector<std::string> &unknown_parameters, std::string &error) = 0;
//virtual bool rescanSpent() = 0;


// TransactionHistory
JNIEXPORT jint JNICALL
Java_io_anonero_model_TransactionHistory_getCount(JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *history = getHandle<Monero::TransactionHistory>(env,
                                                                                instance);
    return history == nullptr ? 0 : history->count();
}

// Keep in sync with Transfer(Long amount, String? address).
jobject newTransferInstance(JNIEnv *env, uint64_t amount, const std::string &address) {
    jmethodID c = env->GetMethodID(class_Transfer, "<init>",
                                   "(JLjava/lang/String;)V");
    if (c == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jstring _address = env->NewStringUTF(address.c_str());
    jobject transfer = env->NewObject(class_Transfer, c, static_cast<jlong> (amount), _address);
    env->DeleteLocalRef(_address);
    if (env->ExceptionCheck()) env->ExceptionClear();
    return transfer;
}

jobject newTransferList(JNIEnv *env, Monero::TransactionInfo *info) {
    if (info == nullptr) return nullptr;
    const std::vector<Monero::TransactionInfo::Transfer> &transfers = info->transfers();
    if (transfers.empty()) { // don't create empty Lists
        return nullptr;
    }
    // make new ArrayList
    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");
    if (java_util_ArrayList_ == nullptr || java_util_ArrayList_add == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jobject result = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                    static_cast<jint> (transfers.size()));
    if (result == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    // create Transfer objects and stick them in the List
    for (const Monero::TransactionInfo::Transfer &s: transfers) {
        jobject element = newTransferInstance(env, s.amount, s.address);
        if (element == nullptr) continue;
        env->CallBooleanMethod(result, java_util_ArrayList_add, element);
        env->DeleteLocalRef(element);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    return result;
}

// Keep this signature and argument order in sync with TransactionInfo's primary constructor.
jobject newTransactionInfo(JNIEnv *env, Monero::TransactionInfo *info) {
    if (info == nullptr) return nullptr;
    jmethodID c = env->GetMethodID(class_TransactionInfo, "<init>",
                                   "(IZZJJJLjava/lang/String;JLjava/lang/String;IIJLjava/lang/String;Ljava/util/List;)V");
    if (c == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jobject transfers = newTransferList(env, info);
    jstring _hash = env->NewStringUTF(info->hash().c_str());
    jstring _paymentId = env->NewStringUTF(info->paymentId().c_str());
    jstring _label = env->NewStringUTF(info->label().c_str());
    uint32_t subaddrIndex = 0;
    if (info->direction() == Monero::TransactionInfo::Direction_In) {
        const std::set<uint32_t> indices = info->subaddrIndex();
        if (!indices.empty()) subaddrIndex = *indices.begin();
    }
    jobject result = env->NewObject(class_TransactionInfo, c,
                                    info->direction(),
                                    info->isPending(),
                                    info->isFailed(),
                                    static_cast<jlong> (info->amount()),
                                    static_cast<jlong> (info->fee()),
                                    static_cast<jlong> (info->blockHeight()),
                                    _hash,
                                    static_cast<jlong> (info->timestamp()),
                                    _paymentId,
                                    static_cast<jint> (info->subaddrAccount()),
                                    static_cast<jint> (subaddrIndex),
                                    static_cast<jlong> (info->confirmations()),
                                    _label,
                                    transfers);
    env->DeleteLocalRef(transfers);
    env->DeleteLocalRef(_hash);
    env->DeleteLocalRef(_paymentId);
    env->DeleteLocalRef(_label);
    if (env->ExceptionCheck()) env->ExceptionClear();
    return result;
}

jobject cpp2java(JNIEnv *env, const std::vector<Monero::TransactionInfo *> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");
    if (java_util_ArrayList_ == nullptr || java_util_ArrayList_add == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                       static_cast<jint> (vector.size()));
    if (arrayList == nullptr || env->ExceptionCheck()) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    for (Monero::TransactionInfo *s: vector) {
        jobject info = newTransactionInfo(env, s);
        if (info == nullptr) continue;
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    return arrayList;
}

JNIEXPORT jobject JNICALL
Java_io_anonero_model_TransactionHistory_refreshJ(JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *history = getHandle<Monero::TransactionHistory>(env,
                                                                                instance);
    if (history == nullptr) return nullptr;
    history->refresh();
    return cpp2java(env, history->getAll());
}

// TransactionInfo is implemented in Java - no need here

JNIEXPORT jint JNICALL
Java_io_anonero_model_PendingTransaction_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    if (tx == nullptr) return static_cast<jint>(Monero::PendingTransaction::Status_Error);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_PendingTransaction_getErrorString(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    if (tx == nullptr) return nullptr;
    return env->NewStringUTF(tx->errorString().c_str());
}

// commit transaction or save to file if filename is provided.
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_PendingTransaction_commit(JNIEnv *env, jobject instance,
                                                jstring filename, jboolean overwrite) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    if (tx == nullptr || filename == nullptr) return JNI_FALSE;
    ScopedUtfChars filenameChars(env, filename);
    if (filenameChars.get() == nullptr) return JNI_FALSE;
    try {
        return static_cast<jboolean>(tx->commit(filenameChars.get(), overwrite));
    } catch (...) {
        LOGE("PendingTransaction.commit failed");
        return JNI_FALSE;
    }
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getAmount(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx == nullptr ? 0 : static_cast<jlong>(tx->amount());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getDust(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx == nullptr ? 0 : static_cast<jlong>(tx->dust());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getFee(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx == nullptr ? 0 : static_cast<jlong>(tx->fee());
}


//Unsigned tx java implementation


JNIEXPORT jint JNICALL
Java_io_anonero_model_UnsignedTransaction_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return static_cast<jint>(Monero::UnsignedTransaction::Status_Critical);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getErrorString(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return nullptr;
    return env->NewStringUTF(tx->errorString().c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getAddress(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return env->NewStringUTF("");

    std::vector<std::string> arr = tx->recipientAddress();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return env->NewStringUTF("");

    return env->NewStringUTF(arr[0].c_str());
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getAmount(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return 0;
    std::vector<uint64_t> arr = tx->amount();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return 0;
    return static_cast<jlong>(arr[0]);
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getFee(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return 0;
    std::vector<uint64_t> arr = tx->fee();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return 0;
    return static_cast<jlong>(arr[0]);
}

// TODO this returns a vector of strings - deal with this later - for now return first one
JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getFirstTxIdJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return nullptr;
    std::vector<std::string> txids = tx->paymentId();
    if (!txids.empty())
        return env->NewStringUTF(txids.front().c_str());
    else
        return nullptr;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getTxCount(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    return tx == nullptr ? 0 : static_cast<jlong>(tx->txCount());
}

// wallet2's own summary of the set, built at load time from splitted_dsts -- the
// outputs sign() actually constructs -- not from the advisory dests the getters
// above read. Same text monero-wallet-cli prints before signing.
JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_confirmationMessageJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr) return env->NewStringUTF("");
    return env->NewStringUTF(tx->confirmationMessage().c_str());
}

// Signs THIS loaded object. Wallet.signAndExportJ re-loads the unsigned file from
// disk and signs whatever is there at that moment; this signs what was reviewed.
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_UnsignedTransaction_signJ(JNIEnv *env, jobject instance,
                                                jstring output_file) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    if (tx == nullptr || output_file == nullptr) return JNI_FALSE;
    ScopedUtfChars outputChars(env, output_file);
    if (outputChars.get() == nullptr) return JNI_FALSE;
    try {
        return static_cast<jboolean>(tx->sign(outputChars.get()));
    } catch (...) {
        LOGE("UnsignedTransaction.sign failed");
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_disposeUnsignedTransaction(JNIEnv *env, jobject,
                                                        jobject unsignedTx) {
    if (unsignedTx == nullptr) return;
    Monero::UnsignedTransaction *tx =
            getHandle<Monero::UnsignedTransaction>(env, unsignedTx);
    if (tx != nullptr) delete tx;
}




// TODO this returns a vector of strings - deal with this later - for now return first one
JNIEXPORT jstring JNICALL
Java_io_anonero_model_PendingTransaction_getFirstTxIdJ(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    if (tx == nullptr) return nullptr;
    std::vector<std::string> txids = tx->txid();
    if (!txids.empty())
        return env->NewStringUTF(txids.front().c_str());
    else
        return nullptr;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getTxCount(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx == nullptr ? 0 : static_cast<jlong>(tx->txCount());
}


// these are all in Monero::Wallet - which I find wrong, so they are here!
//static void init(const char *argv0, const char *default_log_base_name);
//static void debug(const std::string &category, const std::string &str);
//static void info(const std::string &category, const std::string &str);
//static void warning(const std::string &category, const std::string &str);
//static void error(const std::string &category, const std::string &str);
JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_initLogger(JNIEnv *env, jclass,
                                               jstring argv0,
                                               jstring default_log_base_name) {
    if (argv0 == nullptr || default_log_base_name == nullptr) return;
    ScopedUtfChars argv0Chars(env, argv0);
    ScopedUtfChars baseChars(env, default_log_base_name);
    if (argv0Chars.get() == nullptr || baseChars.get() == nullptr) return;
    Monero::Wallet::init(argv0Chars.get(), baseChars.get());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logDebug(JNIEnv *env, jclass,
                                             jstring category, jstring message) {
    if (category == nullptr || message == nullptr) return;
    ScopedUtfChars categoryChars(env, category);
    ScopedUtfChars messageChars(env, message);
    if (categoryChars.get() == nullptr || messageChars.get() == nullptr) return;
    Monero::Wallet::debug(categoryChars.get(), messageChars.get());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logInfo(JNIEnv *env, jclass,
                                            jstring category, jstring message) {
    if (category == nullptr || message == nullptr) return;
    ScopedUtfChars categoryChars(env, category);
    ScopedUtfChars messageChars(env, message);
    if (categoryChars.get() == nullptr || messageChars.get() == nullptr) return;
    Monero::Wallet::info(categoryChars.get(), messageChars.get());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logWarning(JNIEnv *env, jclass,
                                               jstring category, jstring message) {
    if (category == nullptr || message == nullptr) return;
    ScopedUtfChars categoryChars(env, category);
    ScopedUtfChars messageChars(env, message);
    if (categoryChars.get() == nullptr || messageChars.get() == nullptr) return;
    Monero::Wallet::warning(categoryChars.get(), messageChars.get());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logError(JNIEnv *env, jclass,
                                             jstring category, jstring message) {
    if (category == nullptr || message == nullptr) return;
    ScopedUtfChars categoryChars(env, category);
    ScopedUtfChars messageChars(env, message);
    if (categoryChars.get() == nullptr || messageChars.get() == nullptr) return;
    Monero::Wallet::error(categoryChars.get(), messageChars.get());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_setLogLevel(JNIEnv *env, jclass,
                                                jint level) {
    (void) env;
    Monero::WalletManagerFactory::setLogLevel(level);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_WalletManager_moneroVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(MONERO_VERSION);
}

#ifdef __cplusplus
}
#endif
