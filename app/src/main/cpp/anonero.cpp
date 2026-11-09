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

static JavaVM *cachedJVM;
static jclass class_ArrayList;
static jclass class_WalletListener;
static jclass class_TransactionInfo;
static jclass class_Transfer;
static jclass class_WalletStatus;
static jclass class_CoinsInfo;

std::mutex _listenerMutex;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cachedJVM = jvm;
    LOGI("JNI_OnLoad");
    JNIEnv *jenv;
    if (jvm->GetEnv(reinterpret_cast<void **>(&jenv), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    //LOGI("JNI_OnLoad ok");

    class_ArrayList = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("java/util/ArrayList")));
    class_TransactionInfo = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/TransactionInfo")));
    class_Transfer = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/Transfer")));
    class_WalletListener = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/WalletListener")));
    class_WalletStatus = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/Wallet$Status")));
    class_CoinsInfo = static_cast<jclass>(jenv->NewGlobalRef(
            jenv->FindClass("io/anonero/model/CoinsInfo")));
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

struct MyWalletListener : Monero::WalletListener {
    jobject jlistener;

    MyWalletListener(JNIEnv *env, jobject aListener) {
        LOGD("Created Listener");
        jlistener = env->NewGlobalRef(aListener);;
    }

    ~MyWalletListener() {
        LOGD("Destroyed Listener");
    };

    void deleteGlobalJavaRef(JNIEnv *env) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        env->DeleteGlobalRef(jlistener);
        jlistener = nullptr;
    }

/*
 * @brief updated  - generic callback, called when any event (sent/received/block reveived/etc) happened with the wallet;
 */
    void updated() {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("updated");
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jmethodID listenerClass_updated = jenv->GetMethodID(class_WalletListener, "updated", "()V");
        if (listenerClass_updated == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(jlistener, listenerClass_updated);
    }


    /*
     * @brief moneySpent - called when money spent
     * @param txId       - transaction id
     * @param amount     - amount
     */
    void moneySpent(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("moneySpent %"
                     PRIu64, amount);
    }

    /*
     * @brief moneyReceived - called when money received
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void moneyReceived(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("moneyReceived %"
                     PRIu64, amount);
    }

    /*
     * @brief unconfirmedMoneyReceived - called when payment arrived in tx pool
     * @param txId          - transaction id
     * @param amount        - amount
     */
    void unconfirmedMoneyReceived(const std::string &txId, uint64_t amount) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("unconfirmedMoneyReceived %"
                     PRIu64, amount);
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jlong amountLong = static_cast<jlong>(amount);
        jstring jstring1 = jenv->NewStringUTF(txId.c_str());
        ScopedLocalRef txIdRef(jenv, jstring1);
        if (jstring1 == nullptr || jenv->ExceptionCheck()) return;

        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener,
                                                             "unconfirmedMoneyReceived",
                                                             "(Ljava/lang/String;J)V");
        if (listenerClass_newBlock == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(jlistener, listenerClass_newBlock, jstring1, amountLong);
    }

    /*
     * @brief newBlock      - called when new block received
     * @param height        - block height
     */
    void newBlock(uint64_t height) {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        //LOGD("newBlock");
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jlong h = static_cast<jlong>(height);
        jmethodID listenerClass_newBlock = jenv->GetMethodID(class_WalletListener, "newBlock",
                                                             "(J)V");
        if (listenerClass_newBlock == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(jlistener, listenerClass_newBlock, h);
    }

/*
 * @brief refreshed - called when wallet refreshed by background thread or explicitly refreshed by calling "refresh" synchronously
 */
    void refreshed() {
        std::lock_guard<std::mutex> lock(_listenerMutex);
        if (jlistener == nullptr) return;
        LOGD("refreshed");
        ScopedCallbackEnv callbackEnv;
        JNIEnv *jenv = callbackEnv.get();
        if (jenv == nullptr || class_WalletListener == nullptr || jenv->ExceptionCheck()) return;

        jmethodID listenerClass_refreshed = jenv->GetMethodID(class_WalletListener, "refreshed",
                                                              "()V");
        if (listenerClass_refreshed == nullptr || jenv->ExceptionCheck()) return;
        jenv->CallVoidMethod(jlistener, listenerClass_refreshed);
    }
};


//// helper methods
class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv *env, jstring value)
            : env_(env), value_(value), chars_(env->GetStringUTFChars(value, nullptr)) {}

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
    jlong result;
    env->GetLongArrayRegion(arr_j, element, 1, &result);
    return result;
}

std::vector<std::uint64_t> java2cpp_long(JNIEnv *env, jlongArray longArray) {
    std::vector<std::uint64_t> result;
    if (longArray == nullptr) return result;
    jint len = env->GetArrayLength(longArray);
    result.reserve(len);
    for (jint i = 0; i < len; i++) {
        jlong amount = getElement(env, longArray, i);
        result.emplace_back(amount);
    }
    return result;
}

std::string java2cpp_bytes(JNIEnv *env, jbyteArray array) {
    if (array == nullptr) return std::string();
    jsize len = env->GetArrayLength(array);
    std::string result;
    result.resize(len);
    if (len > 0) {
        env->GetByteArrayRegion(array, 0, len, reinterpret_cast<jbyte *>(&result[0]));
    }
    return result;
}

std::vector<std::string> java2cpp_string_array(JNIEnv *env, jobjectArray array) {
    std::vector<std::string> result;
    if (array == nullptr) return result;
    jsize len = env->GetArrayLength(array);
    result.reserve(len);
    for (jsize i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (element == nullptr) {
            result.emplace_back("");
            continue;
        }
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace_back(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

std::set<std::string> java2cpp_set(JNIEnv *env, jobject arrayList) {
    std::set<std::string> result;
    if (arrayList == nullptr) return result;

    jmethodID java_util_ArrayList_size = env->GetMethodID(class_ArrayList, "size", "()I");
    jmethodID java_util_ArrayList_get = env->GetMethodID(class_ArrayList, "get",
                                                         "(I)Ljava/lang/Object;");

    jint len = env->CallIntMethod(arrayList, java_util_ArrayList_size);
    for (jint i = 0; i < len; i++) {
        jstring element = static_cast<jstring>(env->CallObjectMethod(arrayList,
                                                                     java_util_ArrayList_get, i));
        const char *pchars = env->GetStringUTFChars(element, nullptr);
        result.emplace(pchars);
        env->ReleaseStringUTFChars(element, pchars);
        env->DeleteLocalRef(element);
    }
    return result;
}

/// end helpers

#ifdef __cplusplus
extern "C"
{
#endif


/* WalletManager */
JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletJ(JNIEnv *env, jobject instance,
                                                  jstring path,
                                                  jstring password,
                                                  jstring passpharse,
                                                  jstring language,
                                                  jint networkType) {
    std::string seed_words;
    std::string err;
    bool _polyseedCreate = Monero::Wallet::createPolyseed(seed_words, err);
    if (!_polyseedCreate) {
        LOGE("Failed to polyseedCreate");
    }

//     wallet->createPolyseed(seed_words, err);

    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_passpharse = env->GetStringUTFChars(passpharse, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr); // NOT USED ANYMORE?????
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                    std::string(_path),
                    std::string(_password),
                    _networkType,
                    seed_words,
                    std::string(_passpharse), true);

    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(_password), {});
    if (setupStatus == true) {
        LOGD("createWalletJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("createWalletJ(): setupBackgroundSync(): failure!");
    }

    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(passpharse, _passpharse);
    env->ReleaseStringUTFChars(language, _language);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_openWalletJ(JNIEnv *env, jobject instance,
                                                jstring path, jstring password,
                                                jint networkType, jboolean viewOnly) {
    LOGD("openWalletJ(): start");
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->openWallet(
                    std::string(_path),
                    std::string(_password),
                    _networkType);

    if (!viewOnly) {
        // setup background sync
        bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                       std::string(_password), {});
        if (setupStatus == true) {
            LOGD("openWalletJ(): setupBackgroundSync(): success!");
        } else {
            LOGD("openWalletJ(): setupBackgroundSync(): failure!");
        }
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletJ(JNIEnv *env, jobject instance,
                                                    jstring path, jstring password,
                                                    jstring mnemonic, jstring offset,
                                                    jint networkType,
                                                    jlong restoreHeight) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->recoveryWallet(
                    std::string(_path),
                    std::string(_password),
                    std::string(_mnemonic),
                    _networkType,
                    (uint64_t) restoreHeight,
                    1, // kdf_rounds
                    std::string(_offset));
    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(_password), {});
    if (setupStatus == true) {
        LOGD("recoveryWalletJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("recoveryWalletJ(): setupBackgroundSync(): failure!");
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_recoveryWalletPolyseedJ(JNIEnv *env, jobject instance,
                                                            jstring path, jstring password,
                                                            jstring mnemonic, jstring offset,
                                                            jint networkType) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_mnemonic = env->GetStringUTFChars(mnemonic, nullptr);
    const char *_offset = env->GetStringUTFChars(offset, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromPolyseed(
                    std::string(_path),
                    std::string(_password),
                    _networkType,
                    std::string(_mnemonic),
                    std::string(_offset), false);
    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(_password), {});
    if (setupStatus == true) {
        LOGD("recoveryWalletPolyseedJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("recoveryWalletPolyseedJ(): setupBackgroundSync(): failure!");
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(mnemonic, _mnemonic);
    env->ReleaseStringUTFChars(offset, _offset);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletFromKeysJ(JNIEnv *env, jobject instance,
                                                          jstring path, jstring password,
                                                          jstring language,
                                                          jint networkType,
                                                          jlong restoreHeight,
                                                          jstring addressString,
                                                          jstring viewKeyString,
                                                          jstring spendKeyString) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_addressString = env->GetStringUTFChars(addressString, nullptr);
    const char *_viewKeyString = env->GetStringUTFChars(viewKeyString, nullptr);
    const char *_spendKeyString = env->GetStringUTFChars(spendKeyString, nullptr);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromKeys(
                    std::string(_path),
                    std::string(_password),
                    std::string(_language),
                    _networkType,
                    (uint64_t) restoreHeight,
                    std::string(_addressString),
                    std::string(_viewKeyString),
                    std::string(_spendKeyString));
    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(_password), {});
    if (setupStatus == true) {
        LOGD("createWalletFromKeysJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("createWalletFromKeysJ(): setupBackgroundSync(): failure!");
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(language, _language);
    env->ReleaseStringUTFChars(addressString, _addressString);
    env->ReleaseStringUTFChars(viewKeyString, _viewKeyString);
    env->ReleaseStringUTFChars(spendKeyString, _spendKeyString);
    return reinterpret_cast<jlong>(wallet);
}


// virtual void setSubaddressLookahead(uint32_t major, uint32_t minor) = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_createWalletFromDeviceJ(JNIEnv *env, jobject instance,
                                                            jstring path,
                                                            jstring password,
                                                            jint networkType,
                                                            jstring deviceName,
                                                            jlong restoreHeight,
                                                            jstring subaddressLookahead) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_deviceName = env->GetStringUTFChars(deviceName, nullptr);
    const char *_subaddressLookahead = env->GetStringUTFChars(subaddressLookahead, nullptr);

    Monero::Wallet *wallet =
            Monero::WalletManagerFactory::getWalletManager()->createWalletFromDevice(
                    std::string(_path),
                    std::string(_password),
                    _networkType,
                    std::string(_deviceName),
                    (uint64_t) restoreHeight,
                    std::string(_subaddressLookahead));
    bool setupStatus = wallet->setupBackgroundSync(Monero::Wallet::BackgroundSync_ReusePassword,
                                                   std::string(_password), {});
    if (setupStatus == true) {
        LOGD("createWalletFromDeviceJ(): setupBackgroundSync(): success!");
    } else {
        LOGD("createWalletFromDeviceJ(): setupBackgroundSync(): failure!");
    }
    env->ReleaseStringUTFChars(path, _path);
    env->ReleaseStringUTFChars(password, _password);
    env->ReleaseStringUTFChars(deviceName, _deviceName);
    env->ReleaseStringUTFChars(subaddressLookahead, _subaddressLookahead);
    return reinterpret_cast<jlong>(wallet);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_walletExists(JNIEnv *env, jobject instance,
                                                 jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    bool exists =
            Monero::WalletManagerFactory::getWalletManager()->walletExists(std::string(_path));
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(exists);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_verifyWalletPassword(JNIEnv *env, jobject instance,
                                                         jstring keys_file_name,
                                                         jstring password,
                                                         jboolean watch_only) {
    const char *_keys_file_name = env->GetStringUTFChars(keys_file_name, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    bool passwordOk =
            Monero::WalletManagerFactory::getWalletManager()->verifyWalletPassword(
                    std::string(_keys_file_name), std::string(_password), watch_only);
    env->ReleaseStringUTFChars(keys_file_name, _keys_file_name);
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(passwordOk);
}

//virtual int queryWalletHardware(const std::string &keys_file_name, const std::string &password) const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_WalletManager_queryWalletDeviceJ(JNIEnv *env, jobject instance,
                                                       jstring keys_file_name,
                                                       jstring password) {
    const char *_keys_file_name = env->GetStringUTFChars(keys_file_name, nullptr);
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::Wallet::Device device_type;
    bool ok = Monero::WalletManagerFactory::getWalletManager()->
            queryWalletDevice(device_type, std::string(_keys_file_name), std::string(_password));
    env->ReleaseStringUTFChars(keys_file_name, _keys_file_name);
    env->ReleaseStringUTFChars(password, _password);
    if (ok)
        return static_cast<jint>(device_type);
    else
        return -1;
}

//TODO virtual bool checkPayment(const std::string &address, const std::string &txid, const std::string &txkey, const std::string &daemon_address, uint64_t &received, uint64_t &height, std::string &error) const = 0;

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_setDaemonAddressJ(JNIEnv *env, jobject instance,
                                                      jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    LOGD("setDaemonAddressJ(): start");
    Monero::WalletManagerFactory::getWalletManager()->setDaemonAddress(std::string(_address));
    LOGD("setDaemonAddressJ(): end");
    env->ReleaseStringUTFChars(address, _address);
}

// returns whether the daemon can be reached, and its version number
JNIEXPORT jint JNICALL
Java_io_anonero_model_WalletManager_getDaemonVersion(JNIEnv *env,
                                                     jobject instance) {
    uint32_t version;
    bool isConnected =
            Monero::WalletManagerFactory::getWalletManager()->connected(&version);
    if (!isConnected) version = 0;
    return version;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockchainHeight(JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockchainTargetHeight(JNIEnv *env,
                                                              jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->blockchainTargetHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getNetworkDifficulty(JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->networkDifficulty();
}

JNIEXPORT jdouble JNICALL
Java_io_anonero_model_WalletManager_getMiningHashRate(JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->miningHashRate();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_WalletManager_getBlockTarget(JNIEnv *env, jobject instance) {
    return Monero::WalletManagerFactory::getWalletManager()->blockTarget();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_isMining(JNIEnv *env, jobject instance) {
    return static_cast<jboolean>(Monero::WalletManagerFactory::getWalletManager()->isMining());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_startMining(JNIEnv *env, jobject instance,
                                                jstring address,
                                                jboolean background_mining,
                                                jboolean ignore_battery) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    bool success =
            Monero::WalletManagerFactory::getWalletManager()->startMining(std::string(_address),
                                                                          background_mining,
                                                                          ignore_battery);
    env->ReleaseStringUTFChars(address, _address);
    return static_cast<jboolean>(success);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_stopMining(JNIEnv *env, jobject instance) {
    return static_cast<jboolean>(Monero::WalletManagerFactory::getWalletManager()->stopMining());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_WalletManager_resolveOpenAlias(JNIEnv *env, jobject instance,
                                                     jstring address,
                                                     jboolean dnssec_valid) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    bool _dnssec_valid = (bool) dnssec_valid;
    std::string resolvedAlias =
            Monero::WalletManagerFactory::getWalletManager()->resolveOpenAlias(
                    std::string(_address),
                    _dnssec_valid);
    env->ReleaseStringUTFChars(address, _address);
    return env->NewStringUTF(resolvedAlias.c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_setProxyJ(JNIEnv *env, jobject instance,
                                              jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    bool rc =
            Monero::WalletManagerFactory::getWalletManager()->setProxy(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
    return rc;
}


//TODO static std::tuple<bool, std::string, std::string, std::string, std::string> checkUpdates(const std::string &software, const std::string &subdir);

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_WalletManager_closeJ(JNIEnv *env, jobject instance,
                                           jobject walletInstance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, walletInstance);
    bool closeSuccess = Monero::WalletManagerFactory::getWalletManager()->closeWallet(wallet,
                                                                                      false);
    if (closeSuccess) {
        MyWalletListener *walletListener = getHandle<MyWalletListener>(env, walletInstance,
                                                                       "listenerHandle");
        if (walletListener != nullptr) {
            walletListener->deleteGlobalJavaRef(env);
            delete walletListener;
        }
    }
    LOGD("wallet closed");
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
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string _seed;
    std::string seedOffsetStr(_seedOffset);
    bool _getPolyseed = wallet->getPolyseed(_seed, seedOffsetStr);

    if (_getPolyseed) {
        LOGD("_getPolyseed: true");
    } else {
        LOGD("_getPolyseed: false");
        LOGE("wallet->getSeed() %s", wallet->errorString().c_str());
        LOGD("Falling back to normal, boring, seed");
        _seed = wallet->seed(std::string(_seedOffset));
    }

    env->ReleaseStringUTFChars(seedOffset, _seedOffset);

    jstring seed = env->NewStringUTF(_seed.c_str());
    return seed;
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getLegacySeed(JNIEnv *env, jobject instance, jstring seedOffset) {
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string _seed;
    std::string seedOffsetStr(_seedOffset);
    _seed = wallet->seed(std::string(_seedOffset));

    env->ReleaseStringUTFChars(seedOffset, _seedOffset);

    jstring seed = env->NewStringUTF(_seed.c_str());
    return seed;
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isPolyseedSupported(JNIEnv *env, jobject instance,
                                                 jstring seedOffset) {
    const char *_seedOffset = env->GetStringUTFChars(seedOffset, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string _seed;
    std::string seedOffsetStr(_seedOffset);
    bool _getPolyseed = wallet->getPolyseed(_seed, seedOffsetStr);

    env->ReleaseStringUTFChars(seedOffset, _seedOffset);
    return _getPolyseed;
}


JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSeedLanguage(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->getSeedLanguage().c_str());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setSeedLanguage(JNIEnv *env, jobject instance,
                                             jstring language) {
    const char *_language = env->GetStringUTFChars(language, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setSeedLanguage(std::string(_language));
    env->ReleaseStringUTFChars(language, _language);
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->status();
}

jobject newWalletStatusInstance(JNIEnv *env, int status, const std::string &errorString) {
    jmethodID init = env->GetMethodID(class_WalletStatus, "<init>",
                                      "(ILjava/lang/String;)V");
    jstring _errorString = env->NewStringUTF(errorString.c_str());
    jobject instance = env->NewObject(class_WalletStatus, init, status, _errorString);
    env->DeleteLocalRef(_errorString);
    return instance;
}


JNIEXPORT jobject JNICALL
Java_io_anonero_model_Wallet_statusWithErrorString(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    int status;
    std::string errorString;
    wallet->statusWithErrorString(status, errorString);

    return newWalletStatusInstance(env, status, errorString);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setPassword(JNIEnv *env, jobject instance,
                                         jstring password) {
    const char *_password = env->GetStringUTFChars(password, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->setPassword(std::string(_password));
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getAddressJ(JNIEnv *env, jobject instance,
                                         jint accountIndex,
                                         jint addressIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(
            wallet->address((uint32_t) accountIndex, (uint32_t) addressIndex).c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getPath(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->path().c_str());
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_nettype(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->nettype();
}

//TODO virtual void hardForkInfo(uint8_t &version, uint64_t &earliest_height) const = 0;
//TODO virtual bool useForkRules(uint8_t version, int64_t early_blocks) const = 0;

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getIntegratedAddress(JNIEnv *env, jobject instance,
                                                  jstring payment_id) {
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    std::string address = wallet->integratedAddress(_payment_id);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return env->NewStringUTF(address.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSecretViewKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretViewKey().c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSecretSpendKey(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->secretSpendKey().c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_store(JNIEnv *env, jobject instance,
                                   jstring path) {
    const char *_path = env->GetStringUTFChars(path, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = wallet->store(std::string(_path));
    if (!success) {
        LOGE("store(): %s", wallet->errorString().c_str());
    }
    env->ReleaseStringUTFChars(path, _path);
    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getFilename(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return env->NewStringUTF(wallet->filename().c_str());
}

//    virtual std::string keysFilename() const = 0;

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_initJ(JNIEnv *env, jobject instance,
                                   jstring daemon_address,
                                   jlong upper_transaction_size_limit,
                                   jstring daemon_username, jstring daemon_password,
                                   jstring proxy_address, jboolean light_wallet) {
    const char *_daemon_address = env->GetStringUTFChars(daemon_address, nullptr);
    const char *_daemon_username = env->GetStringUTFChars(daemon_username, nullptr);
    const char *_daemon_password = env->GetStringUTFChars(daemon_password, nullptr);
    const char *_proxy_address = env->GetStringUTFChars(proxy_address, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    LOGD("initJ(): start");
    bool status = wallet->init(_daemon_address, (uint64_t) upper_transaction_size_limit,
                               _daemon_username,
                               _daemon_password,
                               false,
                               static_cast<bool>(light_wallet),
                               _proxy_address);
    env->ReleaseStringUTFChars(daemon_address, _daemon_address);
    env->ReleaseStringUTFChars(daemon_username, _daemon_username);
    env->ReleaseStringUTFChars(daemon_password, _daemon_password);
    env->ReleaseStringUTFChars(proxy_address, _proxy_address);
    LOGD("initJ(): end");
    return static_cast<jboolean>(status);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_lightWalletLogin(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
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
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setRefreshFromBlockHeight((uint64_t) height);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getRestoreHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->getRefreshFromBlockHeight();
}

//    virtual void setRecoveringFromSeed(bool recoveringFromSeed) = 0;
//    virtual bool connectToDaemon() = 0;

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getConnectionStatusJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->connected();
}
//TODO virtual void setTrustedDaemon(bool arg) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setTrustedDaemon(JNIEnv *env, jobject instance, jboolean arg) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setTrustedDaemon(arg);
    return static_cast<jboolean>(true);
}

//virtual void setOffline(bool offline) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setOffline(JNIEnv *env, jobject instance, jboolean offline) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setOffline(offline);
}


// TODO virtual bool trustedDaemon() const = 0;

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setProxyJ(JNIEnv *env, jobject instance,
                                       jstring address) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool rc = wallet->setProxy(std::string(_address));
    env->ReleaseStringUTFChars(address, _address);
    return rc;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBalance(JNIEnv *env, jobject instance,
                                        jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->balance((uint32_t) accountIndex);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBalanceAll(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->balanceAll();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getUnlockedBalance(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->unlockedBalance((uint32_t) accountIndex);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getUnlockedBalanceAll(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->unlockedBalanceAll();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isWatchOnly(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->watchOnly());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getBlockChainHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->blockChainHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getApproximateBlockChainHeight(JNIEnv *env,
                                                            jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->approximateBlockChainHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getDaemonBlockChainHeight(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->daemonBlockChainHeight();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getDaemonBlockChainTargetHeight(JNIEnv *env,
                                                             jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->daemonBlockChainTargetHeight();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isSynchronizedJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->synchronized());
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getDeviceTypeJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::Wallet::Device device_type = wallet->getDeviceType();
    return static_cast<jint>(device_type);
}

//void cn_slow_hash(const void *data, size_t length, char *hash); // from crypto/hash-ops.h
JNIEXPORT jbyteArray JNICALL
Java_io_anonero_util_KeyStoreHelper_slowHash(JNIEnv *env, jclass clazz,
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
Java_io_anonero_model_Wallet_getDisplayAmount(JNIEnv *env, jclass clazz,
                                              jlong amount) {
    return env->NewStringUTF(Monero::Wallet::displayAmount(amount).c_str());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getAmountFromString(JNIEnv *env, jclass clazz,
                                                 jstring amount) {
    const char *_amount = env->GetStringUTFChars(amount, nullptr);
    uint64_t x = Monero::Wallet::amountFromString(_amount);
    env->ReleaseStringUTFChars(amount, _amount);
    return x;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getAmountFromDouble(JNIEnv *env, jclass clazz,
                                                 jdouble amount) {
    return Monero::Wallet::amountFromDouble(amount);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_generatePaymentId(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(Monero::Wallet::genPaymentId().c_str());
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isPaymentIdValid(JNIEnv *env, jclass clazz,
                                              jstring payment_id) {
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    bool isValid = Monero::Wallet::paymentIdValid(_payment_id);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_isAddressValid(JNIEnv *env, jclass clazz,
                                            jstring address, jint networkType) {
    const char *_address = env->GetStringUTFChars(address, nullptr);
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    bool isValid = Monero::Wallet::addressValid(_address, _networkType);
    env->ReleaseStringUTFChars(address, _address);
    return static_cast<jboolean>(isValid);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getPaymentIdFromAddress(JNIEnv *env, jclass clazz,
                                                     jstring address,
                                                     jint networkType) {
    Monero::NetworkType _networkType = static_cast<Monero::NetworkType>(networkType);
    const char *_address = env->GetStringUTFChars(address, nullptr);
    std::string payment_id = Monero::Wallet::paymentIdFromAddress(_address, _networkType);
    env->ReleaseStringUTFChars(address, _address);
    return env->NewStringUTF(payment_id.c_str());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getMaximumAllowedAmount(JNIEnv *env, jclass clazz) {
    return Monero::Wallet::maximumAllowedAmount();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_startRefresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->startRefresh();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_pauseRefresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->pauseRefresh();
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_startBackgroundSync(JNIEnv *env, jobject instance) {
    LOGD("startBackgroundSync(): start");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

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
    const char *_password = env->GetStringUTFChars(password, nullptr);
    bool bgsyncStatus = wallet->stopBackgroundSync(std::string(_password));
    if (bgsyncStatus == true) {
        LOGD("stopBackgroundSync(): end: success!");
    } else {
        LOGD("stopBackgroundSync(): end: fail!");
    }
    env->ReleaseStringUTFChars(password, _password);
    return static_cast<jboolean>(bgsyncStatus);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_refresh(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jboolean>(wallet->refresh());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_refreshAsync(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->refreshAsync();
}

//TODO virtual bool rescanBlockchain() = 0;

//virtual void rescanBlockchainAsync() = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_rescanBlockchainAsyncJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->rescanBlockchainAsync();
}


//TODO virtual void setAutoRefreshInterval(int millis) = 0;
//TODO virtual int autoRefreshInterval() const = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createTransactionJ(JNIEnv *env, jobject instance,
                                                jstring dst_addr, jstring payment_id,
                                                jlong amount, jint mixin_count,
                                                jint priority,
                                                jint accountIndex, jobject key_images) {
    const std::set<std::string> _key_images = java2cpp_set(env, key_images);
    const char *_dst_addr = env->GetStringUTFChars(dst_addr, nullptr);
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    Monero::PendingTransaction *tx = wallet->createTransaction(_dst_addr, _payment_id,
                                                               amount, (uint32_t) mixin_count,
                                                               _priority,
                                                               (uint32_t) accountIndex, {},
                                                               _key_images);

    env->ReleaseStringUTFChars(dst_addr, _dst_addr);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createTransactionMultDestJ(JNIEnv *env, jobject instance,
                                                       jobjectArray dst_addr,
                                                       jlongArray amounts,
                                                       jint mixin_count, jint priority,
                                                       jint accountIndex, jobject key_images) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    std::vector<std::string> destinations = java2cpp_string_array(env, dst_addr);
    std::vector<uint64_t> amount_vector = java2cpp_long(env, amounts);
    if (destinations.empty() || destinations.size() != amount_vector.size()) {
        LOGE("createTransactionMultDestJ: destination/amount count mismatch");
        return 0;
    }
    const std::set<std::string> _key_images = java2cpp_set(env, key_images);
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    Monero::optional<std::vector<uint64_t>> amount_opt(amount_vector);
    Monero::PendingTransaction *tx = wallet->createTransactionMultDest(
            destinations, "", amount_opt, (uint32_t) mixin_count, _priority,
            (uint32_t) accountIndex, {}, _key_images);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_estimateTransactionFeeJ(JNIEnv *env, jobject instance,
                                                     jobjectArray destinations,
                                                     jlongArray amounts, jint priority) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    std::vector<std::string> addresses = java2cpp_string_array(env, destinations);
    std::vector<uint64_t> amount_vector = java2cpp_long(env, amounts);
    if (addresses.empty() || addresses.size() != amount_vector.size()) {
        LOGE("estimateTransactionFeeJ: destination/amount count mismatch");
        return 0;
    }
    std::vector<std::pair<std::string, uint64_t>> dest_vector;
    dest_vector.reserve(addresses.size());
    for (size_t i = 0; i < addresses.size(); ++i) {
        dest_vector.emplace_back(addresses[i], amount_vector[i]);
    }
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    try {
        return static_cast<jlong>(wallet->estimateTransactionFee(dest_vector, _priority));
    } catch (const std::exception &e) {
        LOGE("estimateTransactionFeeJ: %s", e.what());
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

    const std::set<std::string> _key_images = java2cpp_set(env, key_images);
    const char *_dst_addr = env->GetStringUTFChars(dst_addr, nullptr);
    const char *_payment_id = env->GetStringUTFChars(payment_id, nullptr);
    Monero::PendingTransaction::Priority _priority =
            static_cast<Monero::PendingTransaction::Priority>(priority);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    Monero::optional<uint64_t> empty;

    Monero::PendingTransaction *tx = wallet->createTransaction(_dst_addr, _payment_id,
                                                               empty, (uint32_t) mixin_count,
                                                               _priority,
                                                               (uint32_t) accountIndex, {},
                                                               _key_images);
    env->ReleaseStringUTFChars(dst_addr, _dst_addr);
    env->ReleaseStringUTFChars(payment_id, _payment_id);
    return reinterpret_cast<jlong>(tx);
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_createSweepUnmixableTransactionJ(JNIEnv *env,
                                                              jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::PendingTransaction *tx = wallet->createSweepUnmixableTransaction();
    return reinterpret_cast<jlong>(tx);
}

//virtual UnsignedTransaction * loadUnsignedTx(const std::string &unsigned_filename) = 0;
//virtual bool submitTransaction(const std::string &fileName) = 0;

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_disposeTransaction(JNIEnv *env, jobject instance,
                                                jobject pendingTransaction) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    Monero::PendingTransaction *_pendingTransaction =
            getHandle<Monero::PendingTransaction>(env, pendingTransaction);
    wallet->disposeTransaction(_pendingTransaction);
}

//virtual bool exportKeyIexportKeyImagesmages(const std::string &filename, bool all = false) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_exportKeyImages(JNIEnv *env, jobject instance, jstring filename,
                                             jboolean all) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (filename == nullptr) return static_cast<jboolean>(false);
    const char *_filename = env->GetStringUTFChars(filename, nullptr);
    if (_filename == nullptr) return static_cast<jboolean>(false);
    bool result = false;
    try {
        result = wallet->exportKeyImages(_filename, all);
    } catch (const std::exception &e) {
        LOGE("exportKeyImages: %s", e.what());
    }
    env->ReleaseStringUTFChars(filename, _filename);
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
    const char *_filename = env->GetStringUTFChars(filename, nullptr);
    if (_filename == nullptr) return env->NewStringUTF("Transaction file is unavailable");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    bool success = false;
    std::string error;
    try {
        success = wallet->submitTransaction(_filename);
        if (!success) error = wallet->errorString();
    } catch (const std::exception &e) {
        error = e.what();
    }
    env->ReleaseStringUTFChars(filename, _filename);
    return env->NewStringUTF(success ? "Transaction submitted!" : error.c_str());
}

//virtual bool exportOutputs(const std::string &filename, bool all = false) = 0;
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_exportOutputs(JNIEnv *env, jobject instance, jstring filename,
                                           jboolean all) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (filename == nullptr) return static_cast<jboolean>(false);
    const char *_filename = env->GetStringUTFChars(filename, nullptr);
    if (_filename == nullptr) return static_cast<jboolean>(false);
    bool result = false;
    try {
        result = wallet->exportOutputs(_filename, all);
    } catch (const std::exception &e) {
        LOGE("exportOutputs: %s", e.what());
    }
    env->ReleaseStringUTFChars(filename, _filename);
    return static_cast<jboolean>(result);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_hasUnknownKeyImages(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->hasUnknownKeyImages();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_viewOnlyBalance(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->viewOnlyBalance(0); // legacy default account
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_viewOnlyBalanceFor(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->viewOnlyBalance((uint32_t) accountIndex);
}

//virtual bool importOutputs(const std::string &filename) = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_importOutputs(JNIEnv *env, jobject instance, jstring filename) {
    if (filename == nullptr) return env->NewStringUTF("Outputs file is missing");
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    const char *_filename = env->GetStringUTFChars(filename, nullptr);
    if (_filename == nullptr) return env->NewStringUTF("Outputs file is unavailable");
    bool success = false;
    std::string error;
    try {
        success = wallet->importOutputs(_filename);
        if (!success) error = wallet->errorString();
    } catch (const std::exception &e) {
        error = e.what();
    }
    env->ReleaseStringUTFChars(filename, _filename);
    return env->NewStringUTF(success ? "Imported" : error.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_signAndExportJ(JNIEnv *env, jobject instance, jstring input_file,
                                            jstring output_file) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    if (input_file == nullptr || output_file == nullptr) {
        return env->NewStringUTF("Unsigned/signed transaction path is missing");
    }
    const char *_input_file = env->GetStringUTFChars(input_file, nullptr);
    const char *_output_file = env->GetStringUTFChars(output_file, nullptr);
    if (_input_file == nullptr || _output_file == nullptr) {
        if (_input_file != nullptr) env->ReleaseStringUTFChars(input_file, _input_file);
        if (_output_file != nullptr) env->ReleaseStringUTFChars(output_file, _output_file);
        return env->NewStringUTF("Transaction path is unavailable");
    }
    bool success = false;
    std::string error;
    try {
        Monero::UnsignedTransaction *tx = wallet->loadUnsignedTx(_input_file);
        if (tx == nullptr) {
            error = "Could not load unsigned transaction";
        } else {
            success = tx->sign(_output_file);
            if (!success) error = wallet->errorString();
        }
    } catch (const std::exception &e) {
        error = e.what();
    }
    env->ReleaseStringUTFChars(input_file, _input_file);
    env->ReleaseStringUTFChars(output_file, _output_file);
    return env->NewStringUTF(success ? "Signed" : error.c_str());
}
JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_loadUnsignedTx(JNIEnv *env, jobject instance, jstring input_file) {
    LOGD("monerujo.cpp: loadUnsignedTx: start");
    if (input_file == nullptr) return 0;
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    const char *_input_file = env->GetStringUTFChars(input_file, nullptr);
    if (_input_file == nullptr) return 0;
    Monero::UnsignedTransaction *tx = nullptr;
    try {
        tx = wallet->loadUnsignedTx(_input_file);
    } catch (...) {
        LOGE("loadUnsignedTx: native load failed");
    }
    env->ReleaseStringUTFChars(input_file, _input_file);
    LOGD("monerujo.cpp: loadUnsignedTx: end");
    return reinterpret_cast<jlong>(tx);
}

//virtual TransactionHistory * history() const = 0;
JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getHistoryJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return reinterpret_cast<jlong>(wallet->history());
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_getCoinsJ(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return reinterpret_cast<jlong>(wallet->coins());
}


jobject newCoinsInfo(JNIEnv *env, Monero::CoinsInfo *info) {
    // Keep this signature in sync with io.anonero.model.CoinsInfo's primary constructor.
    jmethodID c = env->GetMethodID(class_CoinsInfo, "<init>",
                                   "(JZLjava/lang/String;JLjava/lang/String;Ljava/lang/String;ZJZIIJLjava/lang/String;Ljava/lang/String;Z)V");
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
    return result;
}

jobject coins_cpp2java(JNIEnv *env, const std::vector<Monero::CoinsInfo *> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                       static_cast<jint> (vector.size()));
    for (Monero::CoinsInfo *s: vector) {
        jobject info = newCoinsInfo(env, s);
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
    }
    return arrayList;
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Coins_getCount(JNIEnv *env, jobject instance) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    return coins->count();
}

JNIEXPORT jobject JNICALL
Java_io_anonero_model_Coins_refreshJ(JNIEnv *env, jobject instance) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    coins->refresh();
    return coins_cpp2java(env, coins->getAll());
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Coins_setFrozenJ(JNIEnv *env, jobject instance, jstring public_key) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    const char *_public_key = env->GetStringUTFChars(public_key, nullptr);
    try {
        coins->setFrozen(std::string(_public_key));
    } catch (const std::exception &e) {
        LOGE("Coins.setFrozen: %s", e.what());
    }
    env->ReleaseStringUTFChars(public_key, _public_key);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Coins_thawJ(JNIEnv *env, jobject instance, jstring public_key) {
    Monero::Coins *coins = getHandle<Monero::Coins>(env, instance);
    const char *_public_key = env->GetStringUTFChars(public_key, nullptr);
    try {
        coins->thaw(std::string(_public_key));
    } catch (const std::exception &e) {
        LOGE("Coins.thaw: %s", e.what());
    }
    env->ReleaseStringUTFChars(public_key, _public_key);
}


//virtual AddressBook * addressBook() const = 0;

JNIEXPORT jlong JNICALL
Java_io_anonero_model_Wallet_setListenerJ(JNIEnv *env, jobject instance,
                                          jobject javaListener) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setListener(nullptr); // clear old listener
    // delete old listener
    MyWalletListener *oldListener = getHandle<MyWalletListener>(env, instance,
                                                                "listenerHandle");
    if (oldListener != nullptr) {
        oldListener->deleteGlobalJavaRef(env);
        delete oldListener;
    }
    if (javaListener == nullptr) {
        LOGD("null listener");
        return 0;
    } else {
        MyWalletListener *listener = new MyWalletListener(env, javaListener);
        wallet->setListener(listener);
        return reinterpret_cast<jlong>(listener);
    }
}

JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getDefaultMixin(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->defaultMixin();
}

JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setDefaultMixin(JNIEnv *env, jobject instance, jint mixin) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return wallet->setDefaultMixin(mixin);
}

JNIEXPORT jboolean JNICALL
Java_io_anonero_model_Wallet_setUserNote(JNIEnv *env, jobject instance,
                                         jstring txid, jstring note) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);
    const char *_note = env->GetStringUTFChars(note, nullptr);

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    bool success = wallet->setUserNote(_txid, _note);

    env->ReleaseStringUTFChars(txid, _txid);
    env->ReleaseStringUTFChars(note, _note);

    return static_cast<jboolean>(success);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getUserNote(JNIEnv *env, jobject instance,
                                         jstring txid) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string note = wallet->getUserNote(_txid);

    env->ReleaseStringUTFChars(txid, _txid);
    return env->NewStringUTF(note.c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getTxKey(JNIEnv *env, jobject instance,
                                      jstring txid) {

    const char *_txid = env->GetStringUTFChars(txid, nullptr);

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string txKey = wallet->getTxKey(_txid);

    env->ReleaseStringUTFChars(txid, _txid);
    return env->NewStringUTF(txKey.c_str());
}

//virtual void addSubaddressAccount(const std::string& label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_addAccount(JNIEnv *env, jobject instance,
                                        jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->addSubaddressAccount(_label);

    env->ReleaseStringUTFChars(label, _label);
}

//virtual std::string getSubaddressLabel(uint32_t accountIndex, uint32_t addressIndex) const = 0;
JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_getSubaddressLabel(JNIEnv *env, jobject instance,
                                                jint accountIndex, jint addressIndex) {

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);

    std::string label = wallet->getSubaddressLabel((uint32_t) accountIndex,
                                                   (uint32_t) addressIndex);

    return env->NewStringUTF(label.c_str());
}

//virtual void setSubaddressLabel(uint32_t accountIndex, uint32_t addressIndex, const std::string &label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_setSubaddressLabel(JNIEnv *env, jobject instance,
                                                jint accountIndex, jint addressIndex,
                                                jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);

    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->setSubaddressLabel(accountIndex, addressIndex, _label);

    env->ReleaseStringUTFChars(label, _label);
}

// virtual size_t numSubaddressAccounts() const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getNumAccounts(JNIEnv *env, jobject instance) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jint>(wallet->numSubaddressAccounts());
}

//virtual size_t numSubaddresses(uint32_t accountIndex) const = 0;
JNIEXPORT jint JNICALL
Java_io_anonero_model_Wallet_getNumSubaddresses(JNIEnv *env, jobject instance,
                                                jint accountIndex) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    return static_cast<jint>(wallet->numSubaddresses(accountIndex));
}

//virtual void addSubaddress(uint32_t accountIndex, const std::string &label) = 0;
JNIEXPORT void JNICALL
Java_io_anonero_model_Wallet_addSubaddress(JNIEnv *env, jobject instance,
                                           jint accountIndex,
                                           jstring label) {

    const char *_label = env->GetStringUTFChars(label, nullptr);
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
    wallet->addSubaddress(accountIndex, _label);
    env->ReleaseStringUTFChars(label, _label);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_Wallet_signMessageJ(JNIEnv *env, jobject instance, jbyteArray message) {
    Monero::Wallet *wallet = getHandle<Monero::Wallet>(env, instance);
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
    return history->count();
}

jobject newTransferInstance(JNIEnv *env, uint64_t amount, const std::string &address) {
    jmethodID c = env->GetMethodID(class_Transfer, "<init>",
                                   "(JLjava/lang/String;)V");
    jstring _address = env->NewStringUTF(address.c_str());
    jobject transfer = env->NewObject(class_Transfer, c, static_cast<jlong> (amount), _address);
    env->DeleteLocalRef(_address);
    return transfer;
}

jobject newTransferList(JNIEnv *env, Monero::TransactionInfo *info) {
    const std::vector<Monero::TransactionInfo::Transfer> &transfers = info->transfers();
    if (transfers.empty()) { // don't create empty Lists
        return nullptr;
    }
    // make new ArrayList
    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");
    jobject result = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                    static_cast<jint> (transfers.size()));
    // create Transfer objects and stick them in the List
    for (const Monero::TransactionInfo::Transfer &s: transfers) {
        jobject element = newTransferInstance(env, s.amount, s.address);
        env->CallBooleanMethod(result, java_util_ArrayList_add, element);
        env->DeleteLocalRef(element);
    }
    return result;
}

jobject newTransactionInfo(JNIEnv *env, Monero::TransactionInfo *info) {
    jmethodID c = env->GetMethodID(class_TransactionInfo, "<init>",
                                   "(IZZJJJLjava/lang/String;JLjava/lang/String;IIJLjava/lang/String;Ljava/util/List;)V");
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
    return result;
}

jobject cpp2java(JNIEnv *env, const std::vector<Monero::TransactionInfo *> &vector) {

    jmethodID java_util_ArrayList_ = env->GetMethodID(class_ArrayList, "<init>", "(I)V");
    jmethodID java_util_ArrayList_add = env->GetMethodID(class_ArrayList, "add",
                                                         "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(class_ArrayList, java_util_ArrayList_,
                                       static_cast<jint> (vector.size()));
    for (Monero::TransactionInfo *s: vector) {
        jobject info = newTransactionInfo(env, s);
        env->CallBooleanMethod(arrayList, java_util_ArrayList_add, info);
        env->DeleteLocalRef(info);
    }
    return arrayList;
}

JNIEXPORT jobject JNICALL
Java_io_anonero_model_TransactionHistory_refreshJ(JNIEnv *env, jobject instance) {
    Monero::TransactionHistory *history = getHandle<Monero::TransactionHistory>(env,
                                                                                instance);
    history->refresh();
    return cpp2java(env, history->getAll());
}

// TransactionInfo is implemented in Java - no need here

JNIEXPORT jint JNICALL
Java_io_anonero_model_PendingTransaction_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_PendingTransaction_getErrorString(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return env->NewStringUTF(tx->errorString().c_str());
}

// commit transaction or save to file if filename is provided.
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_PendingTransaction_commit(JNIEnv *env, jobject instance,
                                                jstring filename, jboolean overwrite) {

    const char *_filename = env->GetStringUTFChars(filename, nullptr);

    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    bool success = tx->commit(_filename, overwrite);

    env->ReleaseStringUTFChars(filename, _filename);
    return static_cast<jboolean>(success);
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getAmount(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->amount();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getDust(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->dust();
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getFee(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->fee();
}


//Unsigned tx java implementation


JNIEXPORT jint JNICALL
Java_io_anonero_model_UnsignedTransaction_getStatusJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    return tx->status();
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getErrorString(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    return env->NewStringUTF(tx->errorString().c_str());
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getAddress(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);

    std::vector<std::string> arr = tx->recipientAddress();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return env->NewStringUTF("");

    return env->NewStringUTF(arr[0].c_str());
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getAmount(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    std::vector<uint64_t> arr = tx->amount();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return 0;
    return static_cast<jlong>(arr[0]);
}


JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getFee(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    std::vector<uint64_t> arr = tx->fee();
    if (arr.empty())  // size_t: "0 > size() - 1" underflowed on empty input and never fired
        return 0;
    return static_cast<jlong>(arr[0]);
}

// TODO this returns a vector of strings - deal with this later - for now return first one
JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_getFirstTxIdJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    std::vector<std::string> txids = tx->paymentId();
    if (!txids.empty())
        return env->NewStringUTF(txids.front().c_str());
    else
        return nullptr;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_UnsignedTransaction_getTxCount(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    return tx->txCount();
}

// wallet2's own summary of the set, built at load time from splitted_dsts -- the
// outputs sign() actually constructs -- not from the advisory dests the getters
// above read. Same text monero-wallet-cli prints before signing.
JNIEXPORT jstring JNICALL
Java_io_anonero_model_UnsignedTransaction_confirmationMessageJ(JNIEnv *env, jobject instance) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    return env->NewStringUTF(tx->confirmationMessage().c_str());
}

// Signs THIS loaded object. Wallet.signAndExportJ re-loads the unsigned file from
// disk and signs whatever is there at that moment; this signs what was reviewed.
JNIEXPORT jboolean JNICALL
Java_io_anonero_model_UnsignedTransaction_signJ(JNIEnv *env, jobject instance,
                                                jstring output_file) {
    Monero::UnsignedTransaction *tx = getHandle<Monero::UnsignedTransaction>(env, instance);
    const char *_output_file = env->GetStringUTFChars(output_file, nullptr);
    bool success = tx->sign(_output_file);
    env->ReleaseStringUTFChars(output_file, _output_file);
    return static_cast<jboolean>(success);
}




// TODO this returns a vector of strings - deal with this later - for now return first one
JNIEXPORT jstring JNICALL
Java_io_anonero_model_PendingTransaction_getFirstTxIdJ(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    std::vector<std::string> txids = tx->txid();
    if (!txids.empty())
        return env->NewStringUTF(txids.front().c_str());
    else
        return nullptr;
}

JNIEXPORT jlong JNICALL
Java_io_anonero_model_PendingTransaction_getTxCount(JNIEnv *env, jobject instance) {
    Monero::PendingTransaction *tx = getHandle<Monero::PendingTransaction>(env, instance);
    return tx->txCount();
}


// these are all in Monero::Wallet - which I find wrong, so they are here!
//static void init(const char *argv0, const char *default_log_base_name);
//static void debug(const std::string &category, const std::string &str);
//static void info(const std::string &category, const std::string &str);
//static void warning(const std::string &category, const std::string &str);
//static void error(const std::string &category, const std::string &str);
JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_initLogger(JNIEnv *env, jclass clazz,
                                               jstring argv0,
                                               jstring default_log_base_name) {

    const char *_argv0 = env->GetStringUTFChars(argv0, nullptr);
    const char *_default_log_base_name = env->GetStringUTFChars(default_log_base_name, nullptr);

    Monero::Wallet::init(_argv0, _default_log_base_name);

    env->ReleaseStringUTFChars(argv0, _argv0);
    env->ReleaseStringUTFChars(default_log_base_name, _default_log_base_name);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logDebug(JNIEnv *env, jclass clazz,
                                             jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::debug(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logInfo(JNIEnv *env, jclass clazz,
                                            jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::info(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logWarning(JNIEnv *env, jclass clazz,
                                               jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::warning(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_logError(JNIEnv *env, jclass clazz,
                                             jstring category, jstring message) {

    const char *_category = env->GetStringUTFChars(category, nullptr);
    const char *_message = env->GetStringUTFChars(message, nullptr);

    Monero::Wallet::error(_category, _message);

    env->ReleaseStringUTFChars(category, _category);
    env->ReleaseStringUTFChars(message, _message);
}

JNIEXPORT void JNICALL
Java_io_anonero_model_WalletManager_setLogLevel(JNIEnv *env, jclass clazz,
                                                jint level) {
    Monero::WalletManagerFactory::setLogLevel(level);
}

JNIEXPORT jstring JNICALL
Java_io_anonero_model_WalletManager_moneroVersion(JNIEnv *env, jclass clazz) {
    return env->NewStringUTF(MONERO_VERSION);
}

#ifdef __cplusplus
}
#endif
