package com.yeahmobi.everything.repository.mysql;

import com.yeahmobi.everything.auth.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UserRepositoryImpl}.
 * <p>
 * Since we cannot connect to a real MySQL database in unit tests,
 * these tests verify the repository's configuration, SQL constants,
 * and constructor injection behavior.
 * </p>
 */
class UserRepositoryImplTest {

    @Test
    void constructorAcceptsDatabaseManager() {
        MySQLDatabaseManager manager = new MySQLDatabaseManager(
                "jdbc:mysql://localhost:3306/test", "testuser", "testpass");
        UserRepositoryImpl repository = new UserRepositoryImpl(manager);

        assertNotNull(repository);
        assertSame(manager, repository.getDatabaseManager());
    }

    @Test
    void insertSqlContainsAllColumns() {
        String sql = UserRepositoryImpl.INSERT_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("id"), "INSERT SQL should reference id column");
        assertTrue(sql.contains("email"), "INSERT SQL should reference email column");
        assertTrue(sql.contains("password_hash"), "INSERT SQL should reference password_hash column");
        assertTrue(sql.contains("username"), "INSERT SQL should reference username column");
        assertTrue(sql.contains("feishu_user_id"), "INSERT SQL should reference feishu_user_id column");
        assertTrue(sql.contains("login_type"), "INSERT SQL should reference login_type column");
        assertTrue(sql.contains("is_admin"), "INSERT SQL should reference is_admin column");
        assertTrue(sql.contains("created_at"), "INSERT SQL should reference created_at column");
    }

    @Test
    void selectByEmailSqlIsValid() {
        String sql = UserRepositoryImpl.SELECT_BY_EMAIL_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("WHERE email = ?"), "Should query by email parameter");
        assertTrue(sql.contains("FROM user"), "Should query from user table");
    }

    @Test
    void selectByFeishuSqlIsValid() {
        String sql = UserRepositoryImpl.SELECT_BY_FEISHU_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("WHERE feishu_user_id = ?"), "Should query by feishu_user_id parameter");
        assertTrue(sql.contains("FROM user"), "Should query from user table");
    }

    @Test
    void selectByIdSqlIsValid() {
        String sql = UserRepositoryImpl.SELECT_BY_ID_SQL;
        assertNotNull(sql);
        assertTrue(sql.contains("WHERE id = ?"), "Should query by id parameter");
        assertTrue(sql.contains("FROM user"), "Should query from user table");
    }

    @Test
    void userRecordFieldsAreAccessible() {
        User user = new User(
                "uuid-123",
                "test@example.com",
                "hashed_password",
                "TestUser",
                null,
                "email",
                false,
                System.currentTimeMillis()
        );

        assertEquals("uuid-123", user.id());
        assertEquals("test@example.com", user.email());
        assertEquals("hashed_password", user.passwordHash());
        assertEquals("TestUser", user.username());
        assertNull(user.feishuUserId());
        assertEquals("email", user.loginType());
        assertTrue(user.createdAt() > 0);
    }

    @Test
    void userRecordSupportsFeishuLogin() {
        User user = new User(
                "uuid-456",
                "feishu@company.com",
                null,
                "FeishuUser",
                "feishu-id-789",
                "feishu",
                false,
                System.currentTimeMillis()
        );

        assertEquals("feishu-id-789", user.feishuUserId());
        assertNull(user.passwordHash());
        assertEquals("feishu", user.loginType());
    }

    @Test
    void sqlStatementsUseParameterizedQueries() {
        // Verify all SQL statements use parameterized queries (?) to prevent SQL injection
        assertTrue(UserRepositoryImpl.INSERT_SQL.contains("?"),
                "INSERT SQL should use parameterized queries");
        assertTrue(UserRepositoryImpl.SELECT_BY_EMAIL_SQL.contains("?"),
                "SELECT by email SQL should use parameterized queries");
        assertTrue(UserRepositoryImpl.SELECT_BY_FEISHU_SQL.contains("?"),
                "SELECT by feishu SQL should use parameterized queries");
        assertTrue(UserRepositoryImpl.SELECT_BY_ID_SQL.contains("?"),
                "SELECT by id SQL should use parameterized queries");
    }

    @Test
    void insertSqlConvertsEpochMillisToTimestamp() {
        // The INSERT SQL should convert epoch millis to MySQL TIMESTAMP
        String sql = UserRepositoryImpl.INSERT_SQL;
        assertTrue(sql.contains("FROM_UNIXTIME"),
                "INSERT SQL should use FROM_UNIXTIME to convert epoch millis");
    }

    @Test
    void selectSqlConvertsTimestampToEpochMillis() {
        // The SELECT SQL should convert MySQL TIMESTAMP back to epoch millis
        String emailSql = UserRepositoryImpl.SELECT_BY_EMAIL_SQL;
        assertTrue(emailSql.contains("UNIX_TIMESTAMP"),
                "SELECT SQL should use UNIX_TIMESTAMP to convert back to epoch millis");
        assertTrue(emailSql.contains("created_at_millis"),
                "SELECT SQL should alias the converted timestamp");
    }
}
