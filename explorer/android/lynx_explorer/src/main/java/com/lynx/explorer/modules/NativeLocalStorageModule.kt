package com.lynx.explorer.modules

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.lynx.jsbridge.LynxMethod
import com.lynx.jsbridge.LynxModule
import com.lynx.tasm.behavior.LynxContext

class NativeLocalStorageModule(context: Context) : LynxModule(context) {
  private val PREF_NAME = "MyLocalStorage"

  private fun getContext(): Context {
    val lynxContext = mContext as LynxContext
    return lynxContext.getContext()
  }

  @LynxMethod
  fun setStorageItem(key: String, value: String) {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.putString(key, value)
    editor.apply()
  }

  @LynxMethod
  fun getStorageItem(key: String): String? {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    return sharedPreferences.getString(key, null)
  }

  @LynxMethod
  fun clearStorage() {
    val sharedPreferences = getContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    editor.clear()
    editor.apply()
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
}
