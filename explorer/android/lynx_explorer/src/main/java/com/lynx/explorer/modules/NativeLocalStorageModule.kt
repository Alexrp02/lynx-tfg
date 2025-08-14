package com.lynx.explorer.modules

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.edit
import com.lynx.jsbridge.LynxMethod
import com.lynx.jsbridge.LynxModule
import com.lynx.tasm.behavior.LynxContext

class NativeLocalStorageModule(context: Context) : LynxModule(context) {
  private val PREF_NAME = "MyLocalStorage"

  private fun getContext(): Context {
    val lynxContext = mContext as LynxContext
    return lynxContext.context
  }

  @LynxMethod
  fun setStorageItem(key: String, value: String) {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    sharedPreferences.edit { putString(key, value) }
  }

  @LynxMethod
  fun getStorageItem(key: String): String? {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    return sharedPreferences.getString(key, null)
  }

  @LynxMethod
  fun clearStorage() {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    sharedPreferences.edit { clear() }
  }

  @LynxMethod
  fun getImages(): com.lynx.react.bridge.WritableArray {
    val contentResolver: ContentResolver = getContext().contentResolver
    val images = mutableListOf<String>()
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val cursor =
            contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC" // sort by recent
            )

    cursor?.use {
      val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
      while (it.moveToNext()) {
        val id = it.getLong(idColumn)
        val contentUri =
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        .toString()
        images.add(contentUri)
      }
    }

    return com.lynx.react.bridge.JavaOnlyArray.from(images)
  }

  @LynxMethod
  fun endActivity() {
    val lynxContext = mContext as LynxContext
    lynxContext.activity?.finish()
  }

  @LynxMethod
  fun getImageAsUint8Array(url: String): com.lynx.react.bridge.WritableMap? {
    val context = getContext()
    val contentResolver = context.contentResolver
    return try {
      val uri = Uri.parse(url)
      val inputStream = contentResolver.openInputStream(uri)
      val bytes = inputStream?.readBytes()
      inputStream?.close()
      if (bytes != null) {
        val byteArray = com.lynx.react.bridge.JavaOnlyArray()
        for (b in bytes) {
          // Convert signed byte to unsigned int
          byteArray.pushInt(b.toInt() and 0xFF)
        }
        val map = com.lynx.react.bridge.JavaOnlyMap()
        map.putArray("data", byteArray)
        map
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }
}
