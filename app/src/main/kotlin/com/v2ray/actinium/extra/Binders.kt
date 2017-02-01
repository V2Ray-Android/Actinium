/*
 * Copyright 2016. Alex Zhang aka. ztc1997
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.v2ray.actinium.extra

import android.content.Context
import android.os.Parcel
import com.v2ray.actinium.BuildConfig
import com.v2ray.actinium.aidl.IV2RayService
import com.v2ray.actinium.aidl.IV2RayServiceCallback

abstract class IV2RayServiceCallbackStub(val context: Context) : IV2RayServiceCallback.Stub() {
    override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
        var packageName: String? = null
        val packages = context.packageManager.getPackagesForUid(getCallingUid())
        if (packages != null && packages.isNotEmpty()) {
            packageName = packages[0]
        }
        if (packageName != BuildConfig.APPLICATION_ID) {
            return false
        }

        return super.onTransact(code, data, reply, flags)
    }
}

abstract class IV2RayServiceStub(val context: Context) : IV2RayService.Stub() {
    override fun onTransact(code: Int, data: Parcel?, reply: Parcel?, flags: Int): Boolean {
        var packageName: String? = null
        val packages = context.packageManager.getPackagesForUid(getCallingUid())
        if (packages != null && packages.isNotEmpty()) {
            packageName = packages[0]
        }
        if (packageName != BuildConfig.APPLICATION_ID) {
            return false
        }

        return super.onTransact(code, data, reply, flags)
    }
}