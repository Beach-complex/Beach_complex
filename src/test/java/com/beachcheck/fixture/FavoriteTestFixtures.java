package com.beachcheck.fixture;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

import static com.beachcheck.domain.User.Role.USER;
import static java.util.UUID.randomUUID;

/**
 * Why: 테스트 데이터 생성 로직 중복 제거 및 재사용성 향상
 * Policy: 테스트 전용 Fixture이므로 src/test에 위치
 * Contract(Input): 테스트에서 필요한 도메인 객체 생성
 * Contract(Output): 일관된 테스트 데이터 제공
 */
public class FavoriteTestFixtures {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    // ========== User Fixtures ==========

    public static User createUser() {
        return createUser("test@example.com");
    }

    public static User createUser(String email) {
        return createUser(email, "Test User");
    }

    public static User createUser(String email, String name) {
        User user = new User();
        user.setId(randomUUID());
        user.setEmail(email);
        user.setName(name);
        user.setRole(USER);
        user.setPassword("encoded_password");
        user.setEnabled(true);
        return user;
    }

    // ========== Beach Fixtures ==========

    /**
     * Beach 생성 (Unit Test용, location 없음)
     */
    public static Beach createBeach(UUID id) {
        return createBeach(id, "해운대");
    }

    /**
     * Beach 생성 (Unit Test용, location 없음)
     */
    public static Beach createBeach(UUID id, String name) {
        Beach beach = new Beach();
        beach.setId(id);
        beach.setName(name);
        beach.setStatus("OPEN");
        return beach;
    }

    /**
     * Beach 생성 with Location (통합 테스트용, PostGIS Point 포함)
     * Why: DB 저장 시 location 필드가 NOT NULL이므로 필수
     *
     * @param code 해수욕장 코드 (UNIQUE 제약)
     * @param name 해수욕장 이름
     * @param lon 경도 (Longitude)
     * @param lat 위도 (Latitude)
     * @return PostGIS location이 설정된 Beach 객체
     */
    public static Beach createBeachWithLocation(String code, String name, double lon, double lat) {
        Beach beach = new Beach();
        beach.setCode(code);
        beach.setName(name);
        beach.setStatus("OPEN");

        Point location = geometryFactory.createPoint(new Coordinate(lon, lat));
        location.setSRID(4326); // WGS84 좌표계
        beach.setLocation(location);

        return beach;
    }

    // ========== UserFavorite Fixtures ==========

    public static UserFavorite createFavorite(User user, Beach beach) {
        return new UserFavorite(user, beach);
    }

}
