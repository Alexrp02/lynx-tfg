package com.lynx.explorer.modules

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.edit
import com.lynx.jsbridge.LynxMethod
import com.lynx.jsbridge.LynxModule
import com.lynx.tasm.behavior.LynxContext
import android.database.Cursor

class NativeLocalStorageModule(context: Context) : LynxModule(context) {
  private val PREF_NAME = "MyLocalStorage"
  private var dbHelper: DatabaseHelper? = null

  private fun getContext(): Context {
    val lynxContext = mContext as LynxContext
    return lynxContext.context
  }

  // SQLite Database Helper
  private class DatabaseHelper(
    context: Context,
    private val dbName: String,
    private val dbVersion: Int
  ) : SQLiteOpenHelper(context, dbName, null, dbVersion) {
    
    private var creationTables: List<TableDefinition> = emptyList()
    
    data class TableDefinition(val name: String, val sql: String)
    
    fun setTables(tables: List<TableDefinition>) {
      this.creationTables = tables
    }
    
    override fun onCreate(db: SQLiteDatabase) {
      creationTables.forEach { table ->
        db.execSQL(table.sql)
      }
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      // Simple upgrade strategy - you can make this more sophisticated
      creationTables.forEach { table ->
        db.execSQL("DROP TABLE IF EXISTS ${table.name}")
      }
      onCreate(db)
    }
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

  // SQLite methods
  @LynxMethod
  fun initializeDatabase(
    dbName: String, 
    version: Int, 
    tables: com.lynx.react.bridge.ReadableArray
  ): com.lynx.react.bridge.WritableMap {
    val result = com.lynx.react.bridge.JavaOnlyMap()
    
    try {
      val tableDefinitions = mutableListOf<DatabaseHelper.TableDefinition>()
      
      for (i in 0 until tables.size()) {
        val table = tables.getMap(i)
        val tableName = table.getString("name") ?: continue
        val tableSql = table.getString("sql") ?: continue
        tableDefinitions.add(DatabaseHelper.TableDefinition(tableName, tableSql))
      }
      
      dbHelper = DatabaseHelper(getContext(), dbName, version).apply {
        setTables(tableDefinitions)
      }
      
      // Test database creation
      dbHelper?.writableDatabase?.close()
      
      result.putBoolean("success", true)
    } catch (e: Exception) {
      result.putBoolean("success", false)
      result.putString("error", e.message ?: "Unknown error")
    }
    
    return result
  }

  @LynxMethod
  fun executeSql(
    query: String, 
    params: com.lynx.react.bridge.ReadableArray?
  ): com.lynx.react.bridge.WritableMap {
    val result = com.lynx.react.bridge.JavaOnlyMap()
    
    if (dbHelper == null) {
      result.putBoolean("success", false)
      result.putString("error", "Database not initialized. Call initializeDatabase first.")
      return result
    }
    
    try {
      val db = dbHelper!!.writableDatabase
      val args = params?.let { paramsArray ->
        Array(paramsArray.size()) { i ->
          val value = paramsArray.getDynamic(i)
          when {
            value == null -> null
            else -> {
              // Convert dynamic value to string for SQL parameter
              try {
                // Use the dynamic value's toString method or handle special cases
                val stringValue = value.toString()
                // Handle boolean conversion (JavaScript true/false to SQLite 1/0)
                when (stringValue.lowercase()) {
                  "true" -> "1"
                  "false" -> "0"
                  else -> stringValue
                }
              } catch (e: Exception) {
                // Fallback to empty string if conversion fails
                ""
              }
            }
          }
        }
      }
      
      val trimmedQuery = query.trim().uppercase()
      
      when {
        trimmedQuery.startsWith("SELECT") || trimmedQuery.startsWith("PRAGMA") -> {
          // Query that returns data
          val cursor: Cursor = if (args != null) {
            db.rawQuery(query, args)
          } else {
            db.rawQuery(query, null)
          }
          
          val dataArray = com.lynx.react.bridge.JavaOnlyArray()
          
          cursor.use {
            val columnNames = it.columnNames
            while (it.moveToNext()) {
              val row = com.lynx.react.bridge.JavaOnlyMap()
              for (columnName in columnNames) {
                val columnIndex = it.getColumnIndex(columnName)
                when (it.getType(columnIndex)) {
                  Cursor.FIELD_TYPE_NULL -> row.putNull(columnName)
                  Cursor.FIELD_TYPE_INTEGER -> row.putInt(columnName, it.getInt(columnIndex))
                  Cursor.FIELD_TYPE_FLOAT -> row.putDouble(columnName, it.getDouble(columnIndex))
                  Cursor.FIELD_TYPE_STRING -> row.putString(columnName, it.getString(columnIndex))
                  Cursor.FIELD_TYPE_BLOB -> {
                    // Convert blob to base64 string
                    val blob = it.getBlob(columnIndex)
                    val base64 = android.util.Base64.encodeToString(blob, android.util.Base64.DEFAULT)
                    row.putString(columnName, base64)
                  }
                }
              }
              dataArray.pushMap(row)
            }
          }
          
          result.putBoolean("success", true)
          result.putArray("data", dataArray)
        }
        
        trimmedQuery.startsWith("INSERT") -> {
          // Insert statement
          if (args != null) {
            db.execSQL(query, args)
          } else {
            db.execSQL(query)
          }
          
          // Get the last insert ID
          val cursor = db.rawQuery("SELECT last_insert_rowid()", null)
          var insertId = -1L
          cursor.use {
            if (it.moveToFirst()) {
              insertId = it.getLong(0)
            }
          }
          
          result.putBoolean("success", true)
          result.putInt("insertId", insertId.toInt())
          result.putInt("rowsAffected", 1)
        }
        
        trimmedQuery.startsWith("UPDATE") || trimmedQuery.startsWith("DELETE") -> {
          // Update or Delete statement
          if (args != null) {
            db.execSQL(query, args)
          } else {
            db.execSQL(query)
          }
          
          // Get affected rows count
          val cursor = db.rawQuery("SELECT changes()", null)
          var affectedRows = 0
          cursor.use {
            if (it.moveToFirst()) {
              affectedRows = it.getInt(0)
            }
          }
          
          result.putBoolean("success", true)
          result.putInt("rowsAffected", affectedRows)
        }
        
        else -> {
          // DDL statements (CREATE, DROP, ALTER, etc.)
          if (args != null) {
            db.execSQL(query, args)
          } else {
            db.execSQL(query)
          }
          
          result.putBoolean("success", true)
        }
      }
      
    } catch (e: Exception) {
      result.putBoolean("success", false)
      result.putString("error", e.message ?: "Unknown SQL error")
    }
    
    return result
  }
}
