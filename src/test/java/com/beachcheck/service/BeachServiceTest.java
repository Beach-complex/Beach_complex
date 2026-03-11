package com.beachcheck.service;

import static com.beachcheck.fixture.BeachTestFixtures.createBeach;
import static com.beachcheck.fixture.BeachTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.dto.beach.BeachDto;
import com.beachcheck.repository.BeachRepository;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeachService 핵심 분기 테스트")
class BeachServiceTest {

  @Mock private BeachRepository beachRepository;
  @Mock private UserFavoriteService favoriteService;

  @InjectMocks private BeachService beachService;

  @Nested
  @DisplayName("전체 조회")
  class FindAllTests {

    @Test
    @DisplayName("TC-SVC-01: 비로그인 DTO를 반환하고 찜 관련 협력 객체를 호출하지 않는다")
    void tcSvc01_returnAnonymousDtosAndAvoidFavoriteCollaborators() {
      // Given
      UUID beachId1 = UUID.randomUUID();
      UUID beachId2 = UUID.randomUUID();
      Beach beach1 = beach(beachId1, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      Beach beach2 = beach(beachId2, "GWAN", "광안리", "family", "OPEN", 129.12, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(beach1, beach2));

      // When
      List<BeachDto> results = beachService.findAll(null);

      // Then
      assertThat(results).hasSize(2);
      then(favoriteService).should(never()).getFavoriteBeachIds(any());
      then(favoriteService).should(never()).isFavorite(any(), any());
    }

    @Test
    @DisplayName("TC-SVC-02: 찜 ID Set 1회 조회 결과로 찜 여부를 매핑한다")
    void tcSvc02_mapFavoritesFromSingleFavoriteIdSetLookup() {
      // Given
      User user = createUser();
      UUID beachId1 = UUID.randomUUID();
      UUID beachId2 = UUID.randomUUID();
      UUID beachId3 = UUID.randomUUID();
      Beach beach1 = beach(beachId1, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      Beach beach2 = beach(beachId2, "GWAN", "광안리", "family", "OPEN", 129.12, 35.15);
      Beach beach3 = beach(beachId3, "SONG", "송정", "surf", "OPEN", 129.20, 35.18);
      given(beachRepository.findAll()).willReturn(List.of(beach1, beach2, beach3));
      given(favoriteService.getFavoriteBeachIds(user)).willReturn(favoriteIds(beachId1, beachId3));

      // When
      List<BeachDto> results = beachService.findAll(user);

      // Then
      assertThat(results).hasSize(3);
      assertThat(results)
          .extracting(BeachDto::id, BeachDto::isFavorite)
          .containsExactlyInAnyOrder(
              tuple(beachId1, true), tuple(beachId2, false), tuple(beachId3, true));
      then(favoriteService).should().getFavoriteBeachIds(user);
      then(favoriteService).should(never()).isFavorite(any(), any());
    }
  }

  @Nested
  @DisplayName("검색")
  class SearchTests {

    @Test
    @DisplayName("TC-SVC-03: 검색어와 태그가 null이면 전체 목록 경로를 사용한다")
    void tcSvc03_useFindAllPathWhenQAndTagAreNull() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.search(null, null, null);

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.get(0).id()).isEqualTo(beachId);
      then(beachRepository).should().findAll();
      then(beachRepository)
          .should(never())
          .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(anyString(), anyString());
    }

    @Test
    @DisplayName("TC-SVC-04: 비어 있는 검색어와 태그를 null로 정규화한다")
    void tcSvc04_normalizeBlankQAndTagToNull() {
      // Given
      User user = createUser();
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(favoriteService.getFavoriteBeachIds(user)).willReturn(favoriteIds(beachId));

      // When
      List<BeachDto> results = beachService.search("   ", "   ", user);

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.get(0).isFavorite()).isTrue();
      then(beachRepository).should().findAll();
      then(beachRepository)
          .should(never())
          .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(anyString(), anyString());
    }

    @Test
    @DisplayName("TC-SVC-05: repository 검색 전에 검색어를 trim한다")
    void tcSvc05_trimQBeforeRepositorySearch() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("hae", "hae"))
          .willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.search("  hae  ", null, null);

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.get(0).id()).isEqualTo(beachId);
      then(beachRepository)
          .should()
          .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("hae", "hae");
      then(beachRepository).should(never()).findAll();
    }

    @Test
    @DisplayName("TC-SVC-06: repository 검색 후 태그 후처리 필터를 적용한다")
    void tcSvc06_applyTagPostFilterAfterRepositorySearch() {
      // Given
      Beach surf1 = beach(UUID.randomUUID(), "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      Beach surf2 = beach(UUID.randomUUID(), "SONG", "송정", "SURF", "OPEN", 129.20, 35.18);
      Beach family = beach(UUID.randomUUID(), "GWAN", "광안리", "family", "OPEN", 129.12, 35.15);
      Beach nullTag = beach(UUID.randomUUID(), "DADA", "다대포", null, "OPEN", 128.96, 35.05);
      given(beachRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("hae", "hae"))
          .willReturn(List.of(surf1, surf2, family, nullTag));

      // When
      List<BeachDto> results = beachService.search("hae", "  SURF  ", null);

      // Then
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(BeachDto::id, BeachDto::tag)
          .containsExactlyInAnyOrder(tuple(surf1.getId(), "surf"), tuple(surf2.getId(), "SURF"));
      then(beachRepository)
          .should()
          .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("hae", "hae");
    }

    @Test
    @DisplayName("TC-SVC-07: 전체 목록 경로에서 태그 필터와 찜 여부 매핑을 적용한다")
    void tcSvc07_applyTagFilterAndFavoriteMappingOnFindAllPath() {
      // Given
      User user = createUser();
      UUID campFavoriteId = UUID.randomUUID();
      Beach campFavorite = beach(campFavoriteId, "HAE", "해운대", "camp", "OPEN", 129.16, 35.15);
      Beach campNonFavorite =
          beach(UUID.randomUUID(), "GWAN", "광안리", "CAMP", "OPEN", 129.12, 35.15);
      Beach surf = beach(UUID.randomUUID(), "SONG", "송정", "surf", "OPEN", 129.20, 35.18);
      given(beachRepository.findAll()).willReturn(List.of(campFavorite, campNonFavorite, surf));
      given(favoriteService.getFavoriteBeachIds(user)).willReturn(favoriteIds(campFavoriteId));

      // When
      List<BeachDto> results = beachService.search(null, "camp", user);

      // Then
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(BeachDto::id, BeachDto::tag, BeachDto::isFavorite)
          .containsExactlyInAnyOrder(
              tuple(campFavoriteId, "camp", true), tuple(campNonFavorite.getId(), "CAMP", false));
      then(beachRepository).should().findAll();
      then(beachRepository)
          .should(never())
          .findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("반경 검색")
  class FindNearbyTests {

    @Test
    @DisplayName("TC-SVC-08: 경도, 위도, 반경(미터) 값을 repository에 그대로 전달한다")
    void tcSvc08_passLongitudeLatitudeAndRadiusMetersToRepository() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findBeachesWithinRadius(129.16, 35.15, 12500.0))
          .willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.findNearby(129.16, 35.15, 12.5, null);

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.get(0).id()).isEqualTo(beachId);
      then(beachRepository).should().findBeachesWithinRadius(129.16, 35.15, 12500.0);
    }
  }

  @Nested
  @DisplayName("찜 여부 포함 목록")
  class FavoriteAwareListTests {

    @Test
    @DisplayName("TC-SVC-09: 개별 찜 조회 없이 찜 여부 포함 DTO를 반환한다")
    void tcSvc09_returnFavoriteAwareDtosWithoutPerRowFavoriteLookup() {
      // Given
      User user = createUser();
      UUID favoriteId = UUID.randomUUID();
      Beach favoriteBeach = beach(favoriteId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      Beach normalBeach = beach(UUID.randomUUID(), "GWAN", "광안리", "family", "OPEN", 129.12, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(favoriteBeach, normalBeach));
      given(favoriteService.getFavoriteBeachIds(user)).willReturn(favoriteIds(favoriteId));

      // When
      List<BeachDto> results = beachService.getBeachesWithFavorites(user);

      // Then
      assertThat(results).hasSize(2);
      assertThat(results)
          .extracting(BeachDto::id, BeachDto::isFavorite)
          .containsExactlyInAnyOrder(
              tuple(favoriteBeach.getId(), true), tuple(normalBeach.getId(), false));
      then(beachRepository).should().findAll();
      then(favoriteService).should().getFavoriteBeachIds(user);
      then(favoriteService).should(never()).isFavorite(any(), any());
    }

    @Test
    @DisplayName("TC-SVC-10: Point 좌표를 위도는 Y, 경도는 X로 매핑한다")
    void tcSvc10_mapPointCoordinatesAsLatitudeYAndLongitudeX() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.findAll(null);

      // Then
      assertThat(results).hasSize(1);
      assertBeachDto(results.get(0), beachId, "HAE", "해운대", "surf", 35.15, 129.16, false);
    }

    @Test
    @DisplayName("TC-SVC-11: 위치 정보가 없으면 0 좌표를 사용한다")
    void tcSvc11_useZeroCoordinatesWhenLocationIsMissing() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", null, null);
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.findAll(null);

      // Then
      assertThat(results).hasSize(1);
      assertBeachDto(results.get(0), beachId, "HAE", "해운대", "surf", 0.0, 0.0, false);
    }

    @Test
    @DisplayName("TC-SVC-12: getBeachesWithFavorites에서 null user를 비로그인으로 처리한다")
    void tcSvc12_treatNullUserAsAnonymousInGetBeachesWithFavorites() {
      // Given
      UUID beachId = UUID.randomUUID();
      Beach beach = beach(beachId, "HAE", "해운대", "surf", "OPEN", 129.16, 35.15);
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      List<BeachDto> results = beachService.getBeachesWithFavorites(null);

      // Then
      assertThat(results).hasSize(1);
      assertThat(results.get(0).isFavorite()).isFalse();
      then(favoriteService).should(never()).getFavoriteBeachIds(any());
      then(favoriteService).should(never()).isFavorite(any(), any());
    }
  }

  private Beach beach(
      UUID id,
      String code,
      String name,
      String tag,
      String status,
      Double longitude,
      Double latitude) {
    Beach beach =
        longitude == null || latitude == null
            ? createBeach(id, name)
            : createBeachWithLocation(code, name, longitude, latitude);
    beach.setId(id);
    beach.setCode(code);
    beach.setTag(tag);
    beach.setStatus(status);
    return beach;
  }

  private Set<UUID> favoriteIds(UUID... ids) {
    return new HashSet<>(Arrays.asList(ids));
  }

  private void assertBeachDto(
      BeachDto dto,
      UUID id,
      String code,
      String name,
      String tag,
      double latitude,
      double longitude,
      boolean isFavorite) {
    assertThat(dto.id()).isEqualTo(id);
    assertThat(dto.code()).isEqualTo(code);
    assertThat(dto.name()).isEqualTo(name);
    assertThat(dto.tag()).isEqualTo(tag);
    assertThat(dto.latitude()).isEqualTo(latitude);
    assertThat(dto.longitude()).isEqualTo(longitude);
    assertThat(dto.isFavorite()).isEqualTo(isFavorite);
  }
}
