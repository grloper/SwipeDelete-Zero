package com.swipedelete.zero.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BackedUpFileDao_Impl implements BackedUpFileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BackedUpFileEntity> __insertionAdapterOfBackedUpFileEntity;

  private final SharedSQLiteStatement __preparedStmtOfMarkVerified;

  private final SharedSQLiteStatement __preparedStmtOfRemove;

  public BackedUpFileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBackedUpFileEntity = new EntityInsertionAdapter<BackedUpFileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `backed_up_files` (`contentUri`,`sizeBytes`,`remoteId`,`uploadedAtMillis`,`destination`,`displayName`,`remoteState`,`verifiedAtMillis`,`lastError`) VALUES (?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BackedUpFileEntity entity) {
        statement.bindString(1, entity.getContentUri());
        statement.bindLong(2, entity.getSizeBytes());
        statement.bindString(3, entity.getRemoteId());
        statement.bindLong(4, entity.getUploadedAtMillis());
        statement.bindString(5, entity.getDestination());
        statement.bindString(6, entity.getDisplayName());
        statement.bindString(7, entity.getRemoteState());
        statement.bindLong(8, entity.getVerifiedAtMillis());
        if (entity.getLastError() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getLastError());
        }
      }
    };
    this.__preparedStmtOfMarkVerified = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE backed_up_files SET remoteState = ?, verifiedAtMillis = ?, lastError = ? WHERE contentUri = ?";
        return _query;
      }
    };
    this.__preparedStmtOfRemove = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM backed_up_files WHERE contentUri = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final BackedUpFileEntity file,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBackedUpFileEntity.insert(file);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markVerified(final String uri, final String state, final long at,
      final String error, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkVerified.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, state);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, at);
        _argIndex = 3;
        if (error == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, error);
        }
        _argIndex = 4;
        _stmt.bindString(_argIndex, uri);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkVerified.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object remove(final String uri, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfRemove.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, uri);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfRemove.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> observeCount() {
    final String _sql = "SELECT COUNT(*) FROM backed_up_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"backed_up_files"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<String>> observeBackedUpUris() {
    final String _sql = "SELECT contentUri FROM backed_up_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"backed_up_files"}, new Callable<List<String>>() {
      @Override
      @NonNull
      public List<String> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final List<String> _result = new ArrayList<String>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final String _item;
            _item = _cursor.getString(0);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<BackedUpFileEntity>> observeAll() {
    final String _sql = "SELECT * FROM backed_up_files ORDER BY uploadedAtMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"backed_up_files"}, new Callable<List<BackedUpFileEntity>>() {
      @Override
      @NonNull
      public List<BackedUpFileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
          final int _cursorIndexOfUploadedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadedAtMillis");
          final int _cursorIndexOfDestination = CursorUtil.getColumnIndexOrThrow(_cursor, "destination");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfRemoteState = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteState");
          final int _cursorIndexOfVerifiedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "verifiedAtMillis");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final List<BackedUpFileEntity> _result = new ArrayList<BackedUpFileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BackedUpFileEntity _item;
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpRemoteId;
            _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
            final long _tmpUploadedAtMillis;
            _tmpUploadedAtMillis = _cursor.getLong(_cursorIndexOfUploadedAtMillis);
            final String _tmpDestination;
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpRemoteState;
            _tmpRemoteState = _cursor.getString(_cursorIndexOfRemoteState);
            final long _tmpVerifiedAtMillis;
            _tmpVerifiedAtMillis = _cursor.getLong(_cursorIndexOfVerifiedAtMillis);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            _item = new BackedUpFileEntity(_tmpContentUri,_tmpSizeBytes,_tmpRemoteId,_tmpUploadedAtMillis,_tmpDestination,_tmpDisplayName,_tmpRemoteState,_tmpVerifiedAtMillis,_tmpLastError);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAll(final Continuation<? super List<BackedUpFileEntity>> $completion) {
    final String _sql = "SELECT * FROM backed_up_files";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BackedUpFileEntity>>() {
      @Override
      @NonNull
      public List<BackedUpFileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
          final int _cursorIndexOfUploadedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadedAtMillis");
          final int _cursorIndexOfDestination = CursorUtil.getColumnIndexOrThrow(_cursor, "destination");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfRemoteState = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteState");
          final int _cursorIndexOfVerifiedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "verifiedAtMillis");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final List<BackedUpFileEntity> _result = new ArrayList<BackedUpFileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BackedUpFileEntity _item;
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpRemoteId;
            _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
            final long _tmpUploadedAtMillis;
            _tmpUploadedAtMillis = _cursor.getLong(_cursorIndexOfUploadedAtMillis);
            final String _tmpDestination;
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpRemoteState;
            _tmpRemoteState = _cursor.getString(_cursorIndexOfRemoteState);
            final long _tmpVerifiedAtMillis;
            _tmpVerifiedAtMillis = _cursor.getLong(_cursorIndexOfVerifiedAtMillis);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            _item = new BackedUpFileEntity(_tmpContentUri,_tmpSizeBytes,_tmpRemoteId,_tmpUploadedAtMillis,_tmpDestination,_tmpDisplayName,_tmpRemoteState,_tmpVerifiedAtMillis,_tmpLastError);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String uri, final Continuation<? super BackedUpFileEntity> $completion) {
    final String _sql = "SELECT * FROM backed_up_files WHERE contentUri = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uri);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BackedUpFileEntity>() {
      @Override
      @Nullable
      public BackedUpFileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfRemoteId = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteId");
          final int _cursorIndexOfUploadedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadedAtMillis");
          final int _cursorIndexOfDestination = CursorUtil.getColumnIndexOrThrow(_cursor, "destination");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfRemoteState = CursorUtil.getColumnIndexOrThrow(_cursor, "remoteState");
          final int _cursorIndexOfVerifiedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "verifiedAtMillis");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final BackedUpFileEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpRemoteId;
            _tmpRemoteId = _cursor.getString(_cursorIndexOfRemoteId);
            final long _tmpUploadedAtMillis;
            _tmpUploadedAtMillis = _cursor.getLong(_cursorIndexOfUploadedAtMillis);
            final String _tmpDestination;
            _tmpDestination = _cursor.getString(_cursorIndexOfDestination);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpRemoteState;
            _tmpRemoteState = _cursor.getString(_cursorIndexOfRemoteState);
            final long _tmpVerifiedAtMillis;
            _tmpVerifiedAtMillis = _cursor.getLong(_cursorIndexOfVerifiedAtMillis);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            _result = new BackedUpFileEntity(_tmpContentUri,_tmpSizeBytes,_tmpRemoteId,_tmpUploadedAtMillis,_tmpDestination,_tmpDisplayName,_tmpRemoteState,_tmpVerifiedAtMillis,_tmpLastError);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<Integer> observeCountFor(final String destination) {
    final String _sql = "SELECT COUNT(*) FROM backed_up_files WHERE destination = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, destination);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"backed_up_files"}, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
