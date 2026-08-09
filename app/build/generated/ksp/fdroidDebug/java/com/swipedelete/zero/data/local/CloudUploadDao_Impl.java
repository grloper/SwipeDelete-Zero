package com.swipedelete.zero.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
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
public final class CloudUploadDao_Impl implements CloudUploadDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDeleteIfQueued;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final EntityUpsertionAdapter<CloudUploadEntity> __upsertionAdapterOfCloudUploadEntity;

  public CloudUploadDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDeleteIfQueued = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cloud_uploads WHERE contentUri = ? AND state = 'QUEUED'";
        return _query;
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cloud_uploads WHERE contentUri = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfCloudUploadEntity = new EntityUpsertionAdapter<CloudUploadEntity>(new EntityInsertionAdapter<CloudUploadEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `cloud_uploads` (`contentUri`,`displayName`,`mimeType`,`sizeBytes`,`state`,`uploadUrl`,`bytesUploaded`,`uploadToken`,`mediaItemId`,`attempts`,`lastError`,`enqueuedAtMillis`,`updatedAtMillis`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CloudUploadEntity entity) {
        statement.bindString(1, entity.getContentUri());
        statement.bindString(2, entity.getDisplayName());
        statement.bindString(3, entity.getMimeType());
        statement.bindLong(4, entity.getSizeBytes());
        statement.bindString(5, entity.getState());
        if (entity.getUploadUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getUploadUrl());
        }
        statement.bindLong(7, entity.getBytesUploaded());
        if (entity.getUploadToken() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getUploadToken());
        }
        if (entity.getMediaItemId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getMediaItemId());
        }
        statement.bindLong(10, entity.getAttempts());
        if (entity.getLastError() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLastError());
        }
        statement.bindLong(12, entity.getEnqueuedAtMillis());
        statement.bindLong(13, entity.getUpdatedAtMillis());
      }
    }, new EntityDeletionOrUpdateAdapter<CloudUploadEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `cloud_uploads` SET `contentUri` = ?,`displayName` = ?,`mimeType` = ?,`sizeBytes` = ?,`state` = ?,`uploadUrl` = ?,`bytesUploaded` = ?,`uploadToken` = ?,`mediaItemId` = ?,`attempts` = ?,`lastError` = ?,`enqueuedAtMillis` = ?,`updatedAtMillis` = ? WHERE `contentUri` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CloudUploadEntity entity) {
        statement.bindString(1, entity.getContentUri());
        statement.bindString(2, entity.getDisplayName());
        statement.bindString(3, entity.getMimeType());
        statement.bindLong(4, entity.getSizeBytes());
        statement.bindString(5, entity.getState());
        if (entity.getUploadUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getUploadUrl());
        }
        statement.bindLong(7, entity.getBytesUploaded());
        if (entity.getUploadToken() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getUploadToken());
        }
        if (entity.getMediaItemId() == null) {
          statement.bindNull(9);
        } else {
          statement.bindString(9, entity.getMediaItemId());
        }
        statement.bindLong(10, entity.getAttempts());
        if (entity.getLastError() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getLastError());
        }
        statement.bindLong(12, entity.getEnqueuedAtMillis());
        statement.bindLong(13, entity.getUpdatedAtMillis());
        statement.bindString(14, entity.getContentUri());
      }
    });
  }

  @Override
  public Object deleteIfQueued(final String uri, final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteIfQueued.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, uri);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteIfQueued.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String uri, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
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
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final CloudUploadEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfCloudUploadEntity.upsert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CloudUploadEntity>> observeAll() {
    final String _sql = "SELECT * FROM cloud_uploads ORDER BY enqueuedAtMillis";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cloud_uploads"}, new Callable<List<CloudUploadEntity>>() {
      @Override
      @NonNull
      public List<CloudUploadEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfUploadUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadUrl");
          final int _cursorIndexOfBytesUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "bytesUploaded");
          final int _cursorIndexOfUploadToken = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadToken");
          final int _cursorIndexOfMediaItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaItemId");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final int _cursorIndexOfEnqueuedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "enqueuedAtMillis");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final List<CloudUploadEntity> _result = new ArrayList<CloudUploadEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CloudUploadEntity _item;
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final String _tmpUploadUrl;
            if (_cursor.isNull(_cursorIndexOfUploadUrl)) {
              _tmpUploadUrl = null;
            } else {
              _tmpUploadUrl = _cursor.getString(_cursorIndexOfUploadUrl);
            }
            final long _tmpBytesUploaded;
            _tmpBytesUploaded = _cursor.getLong(_cursorIndexOfBytesUploaded);
            final String _tmpUploadToken;
            if (_cursor.isNull(_cursorIndexOfUploadToken)) {
              _tmpUploadToken = null;
            } else {
              _tmpUploadToken = _cursor.getString(_cursorIndexOfUploadToken);
            }
            final String _tmpMediaItemId;
            if (_cursor.isNull(_cursorIndexOfMediaItemId)) {
              _tmpMediaItemId = null;
            } else {
              _tmpMediaItemId = _cursor.getString(_cursorIndexOfMediaItemId);
            }
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            final long _tmpEnqueuedAtMillis;
            _tmpEnqueuedAtMillis = _cursor.getLong(_cursorIndexOfEnqueuedAtMillis);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _item = new CloudUploadEntity(_tmpContentUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpState,_tmpUploadUrl,_tmpBytesUploaded,_tmpUploadToken,_tmpMediaItemId,_tmpAttempts,_tmpLastError,_tmpEnqueuedAtMillis,_tmpUpdatedAtMillis);
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
  public Object get(final String uri, final Continuation<? super CloudUploadEntity> $completion) {
    final String _sql = "SELECT * FROM cloud_uploads WHERE contentUri = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, uri);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CloudUploadEntity>() {
      @Override
      @Nullable
      public CloudUploadEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfUploadUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadUrl");
          final int _cursorIndexOfBytesUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "bytesUploaded");
          final int _cursorIndexOfUploadToken = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadToken");
          final int _cursorIndexOfMediaItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaItemId");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final int _cursorIndexOfEnqueuedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "enqueuedAtMillis");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final CloudUploadEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final String _tmpUploadUrl;
            if (_cursor.isNull(_cursorIndexOfUploadUrl)) {
              _tmpUploadUrl = null;
            } else {
              _tmpUploadUrl = _cursor.getString(_cursorIndexOfUploadUrl);
            }
            final long _tmpBytesUploaded;
            _tmpBytesUploaded = _cursor.getLong(_cursorIndexOfBytesUploaded);
            final String _tmpUploadToken;
            if (_cursor.isNull(_cursorIndexOfUploadToken)) {
              _tmpUploadToken = null;
            } else {
              _tmpUploadToken = _cursor.getString(_cursorIndexOfUploadToken);
            }
            final String _tmpMediaItemId;
            if (_cursor.isNull(_cursorIndexOfMediaItemId)) {
              _tmpMediaItemId = null;
            } else {
              _tmpMediaItemId = _cursor.getString(_cursorIndexOfMediaItemId);
            }
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            final long _tmpEnqueuedAtMillis;
            _tmpEnqueuedAtMillis = _cursor.getLong(_cursorIndexOfEnqueuedAtMillis);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _result = new CloudUploadEntity(_tmpContentUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpState,_tmpUploadUrl,_tmpBytesUploaded,_tmpUploadToken,_tmpMediaItemId,_tmpAttempts,_tmpLastError,_tmpEnqueuedAtMillis,_tmpUpdatedAtMillis);
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
  public Object nextPending(final Continuation<? super CloudUploadEntity> $completion) {
    final String _sql = "SELECT * FROM cloud_uploads WHERE state IN ('QUEUED', 'UPLOADING', 'VERIFYING') ORDER BY enqueuedAtMillis LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CloudUploadEntity>() {
      @Override
      @Nullable
      public CloudUploadEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContentUri = CursorUtil.getColumnIndexOrThrow(_cursor, "contentUri");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMimeType = CursorUtil.getColumnIndexOrThrow(_cursor, "mimeType");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
          final int _cursorIndexOfUploadUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadUrl");
          final int _cursorIndexOfBytesUploaded = CursorUtil.getColumnIndexOrThrow(_cursor, "bytesUploaded");
          final int _cursorIndexOfUploadToken = CursorUtil.getColumnIndexOrThrow(_cursor, "uploadToken");
          final int _cursorIndexOfMediaItemId = CursorUtil.getColumnIndexOrThrow(_cursor, "mediaItemId");
          final int _cursorIndexOfAttempts = CursorUtil.getColumnIndexOrThrow(_cursor, "attempts");
          final int _cursorIndexOfLastError = CursorUtil.getColumnIndexOrThrow(_cursor, "lastError");
          final int _cursorIndexOfEnqueuedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "enqueuedAtMillis");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final CloudUploadEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpContentUri;
            _tmpContentUri = _cursor.getString(_cursorIndexOfContentUri);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMimeType;
            _tmpMimeType = _cursor.getString(_cursorIndexOfMimeType);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpState;
            _tmpState = _cursor.getString(_cursorIndexOfState);
            final String _tmpUploadUrl;
            if (_cursor.isNull(_cursorIndexOfUploadUrl)) {
              _tmpUploadUrl = null;
            } else {
              _tmpUploadUrl = _cursor.getString(_cursorIndexOfUploadUrl);
            }
            final long _tmpBytesUploaded;
            _tmpBytesUploaded = _cursor.getLong(_cursorIndexOfBytesUploaded);
            final String _tmpUploadToken;
            if (_cursor.isNull(_cursorIndexOfUploadToken)) {
              _tmpUploadToken = null;
            } else {
              _tmpUploadToken = _cursor.getString(_cursorIndexOfUploadToken);
            }
            final String _tmpMediaItemId;
            if (_cursor.isNull(_cursorIndexOfMediaItemId)) {
              _tmpMediaItemId = null;
            } else {
              _tmpMediaItemId = _cursor.getString(_cursorIndexOfMediaItemId);
            }
            final int _tmpAttempts;
            _tmpAttempts = _cursor.getInt(_cursorIndexOfAttempts);
            final String _tmpLastError;
            if (_cursor.isNull(_cursorIndexOfLastError)) {
              _tmpLastError = null;
            } else {
              _tmpLastError = _cursor.getString(_cursorIndexOfLastError);
            }
            final long _tmpEnqueuedAtMillis;
            _tmpEnqueuedAtMillis = _cursor.getLong(_cursorIndexOfEnqueuedAtMillis);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _result = new CloudUploadEntity(_tmpContentUri,_tmpDisplayName,_tmpMimeType,_tmpSizeBytes,_tmpState,_tmpUploadUrl,_tmpBytesUploaded,_tmpUploadToken,_tmpMediaItemId,_tmpAttempts,_tmpLastError,_tmpEnqueuedAtMillis,_tmpUpdatedAtMillis);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
