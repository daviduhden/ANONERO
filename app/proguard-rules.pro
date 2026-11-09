#
# Copyright (c) 2026, David Uhden Collado
# Copyright (c) 2022-2026, r4v3r23
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

 -if @kotlinx.serialization.Serializable class **
 -keepclassmembers class <1> {
     static <1>$Companion Companion;
 }

 # Keep `serializer()` on companion objects (both default and named) of serializable classes.
 -if @kotlinx.serialization.Serializable class ** {
     static **$* *;
 }
 -keepclassmembers class <2>$<3> {
     kotlinx.serialization.KSerializer serializer(...);
 }

 # Keep `INSTANCE.serializer()` of serializable objects.
 -if @kotlinx.serialization.Serializable class ** {
     public static ** INSTANCE;
 }
 -keepclassmembers class <1> {
     public static <1> INSTANCE;
     kotlinx.serialization.KSerializer serializer(...);
 }

 # @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
 -keepattributes RuntimeVisibleAnnotations,AnnotationDefault

 # Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
 # See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
 -dontnote kotlinx.serialization.**

 # Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
 # If there is no `java.lang.ClassValue` (for example, in Android), then R8/ProGuard will print a warning.
 # However, since in this case they will not be used, we can disable these warnings
 -dontwarn kotlinx.serialization.internal.ClassValueReferences

 # disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
 # see https://github.com/Kotlin/kotlinx.serialization/issues/2719
 -keepclassmembers public class **$$serializer {
     private ** descriptor;
 }
