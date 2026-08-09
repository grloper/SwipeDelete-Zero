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
public final class DeckSessionDao_Impl implements DeckSessionDao {
  private final RoomDatabase __db;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final EntityUpsertionAdapter<DeckSessionEntity> __upsertionAdapterOfDeckSessionEntity;

  public DeckSessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM deck_sessions WHERE deckId = ?";
        return _query;
      }
    };
    this.__upsertionAdapterOfDeckSessionEntity = new EntityUpsertionAdapter<DeckSessionEntity>(new EntityInsertionAdapter<DeckSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `deck_sessions` (`deckId`,`kind`,`title`,`cursor`,`totalCount`,`updatedAtMillis`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DeckSessionEntity entity) {
        statement.bindString(1, entity.getDeckId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getCursor());
        statement.bindLong(5, entity.getTotalCount());
        statement.bindLong(6, entity.getUpdatedAtMillis());
      }
    }, new EntityDeletionOrUpdateAdapter<DeckSessionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `deck_sessions` SET `deckId` = ?,`kind` = ?,`title` = ?,`cursor` = ?,`totalCount` = ?,`updatedAtMillis` = ? WHERE `deckId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DeckSessionEntity entity) {
        statement.bindString(1, entity.getDeckId());
        statement.bindString(2, entity.getKind());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getCursor());
        statement.bindLong(5, entity.getTotalCount());
        statement.bindLong(6, entity.getUpdatedAtMillis());
        statement.bindString(7, entity.getDeckId());
      }
    });
  }

  @Override
  public Object delete(final String deckId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, deckId);
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
  public Object upsert(final DeckSessionEntity session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfDeckSessionEntity.upsert(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object get(final String deckId,
      final Continuation<? super DeckSessionEntity> $completion) {
    final String _sql = "SELECT * FROM deck_sessions WHERE deckId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, deckId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DeckSessionEntity>() {
      @Override
      @Nullable
      public DeckSessionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeckId = CursorUtil.getColumnIndexOrThrow(_cursor, "deckId");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCursor = CursorUtil.getColumnIndexOrThrow(_cursor, "cursor");
          final int _cursorIndexOfTotalCount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCount");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final DeckSessionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDeckId;
            _tmpDeckId = _cursor.getString(_cursorIndexOfDeckId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpCursor;
            _tmpCursor = _cursor.getInt(_cursorIndexOfCursor);
            final int _tmpTotalCount;
            _tmpTotalCount = _cursor.getInt(_cursorIndexOfTotalCount);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _result = new DeckSessionEntity(_tmpDeckId,_tmpKind,_tmpTitle,_tmpCursor,_tmpTotalCount,_tmpUpdatedAtMillis);
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
  public Flow<List<DeckSessionEntity>> observeAll() {
    final String _sql = "SELECT * FROM deck_sessions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"deck_sessions"}, new Callable<List<DeckSessionEntity>>() {
      @Override
      @NonNull
      public List<DeckSessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeckId = CursorUtil.getColumnIndexOrThrow(_cursor, "deckId");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCursor = CursorUtil.getColumnIndexOrThrow(_cursor, "cursor");
          final int _cursorIndexOfTotalCount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCount");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final List<DeckSessionEntity> _result = new ArrayList<DeckSessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DeckSessionEntity _item;
            final String _tmpDeckId;
            _tmpDeckId = _cursor.getString(_cursorIndexOfDeckId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpCursor;
            _tmpCursor = _cursor.getInt(_cursorIndexOfCursor);
            final int _tmpTotalCount;
            _tmpTotalCount = _cursor.getInt(_cursorIndexOfTotalCount);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _item = new DeckSessionEntity(_tmpDeckId,_tmpKind,_tmpTitle,_tmpCursor,_tmpTotalCount,_tmpUpdatedAtMillis);
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
  public Object getAll(final Continuation<? super List<DeckSessionEntity>> $completion) {
    final String _sql = "SELECT * FROM deck_sessions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DeckSessionEntity>>() {
      @Override
      @NonNull
      public List<DeckSessionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDeckId = CursorUtil.getColumnIndexOrThrow(_cursor, "deckId");
          final int _cursorIndexOfKind = CursorUtil.getColumnIndexOrThrow(_cursor, "kind");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfCursor = CursorUtil.getColumnIndexOrThrow(_cursor, "cursor");
          final int _cursorIndexOfTotalCount = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCount");
          final int _cursorIndexOfUpdatedAtMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAtMillis");
          final List<DeckSessionEntity> _result = new ArrayList<DeckSessionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DeckSessionEntity _item;
            final String _tmpDeckId;
            _tmpDeckId = _cursor.getString(_cursorIndexOfDeckId);
            final String _tmpKind;
            _tmpKind = _cursor.getString(_cursorIndexOfKind);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpCursor;
            _tmpCursor = _cursor.getInt(_cursorIndexOfCursor);
            final int _tmpTotalCount;
            _tmpTotalCount = _cursor.getInt(_cursorIndexOfTotalCount);
            final long _tmpUpdatedAtMillis;
            _tmpUpdatedAtMillis = _cursor.getLong(_cursorIndexOfUpdatedAtMillis);
            _item = new DeckSessionEntity(_tmpDeckId,_tmpKind,_tmpTitle,_tmpCursor,_tmpTotalCount,_tmpUpdatedAtMillis);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
