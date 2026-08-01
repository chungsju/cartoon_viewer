# 안드로이드 만화책 뷰어 어플 구현 계획

이 계획은 `https://update.spotv24.com/` 웹사이트의 컨텐츠를 파싱하여 제공하는 만화책 뷰어 앱의 전체 구현 과정을 담고 있습니다.

## User Review Required

> [!IMPORTANT]
> **웹 크롤링 우회**: 대상 사이트의 크롤링 방지 정책에 대응하기 위해 모바일 브라우저 환경을 모방한 `User-Agent` 및 `Referer` 헤더를 사용할 예정입니다. 만약 단순 헤더 조작으로 부족할 경우, 세션 유지를 위한 `WebView` 기반 쿠키 획득 로직이 추가될 수 있습니다.

> [!NOTE]
> **뷰어 기능**: 이미지 두 장 보기 및 한 장 나누기 기능은 기기의 화면 방향(가로/세로)에 따라 사용자 경험이 달라질 수 있습니다. 초기 버전은 수동 설정을 기준으로 구현합니다.

## Proposed Changes

### 1. 환경 설정 및 의존성 추가
- [Jsoup](https://jsoup.org/): HTML 파싱을 위해 사용
- [Coil](https://coil-kt.github.io/coil/): 이미지 로딩 및 캐싱
- [OkHttp](https://square.github.io/okhttp/): 네트워크 통신 및 헤더 관리
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation): 화면 간 이동 관리

### 2. 데이터 레이어 (Scraping & Models)
- **Data Models**: `Manga`, `Chapter`, `MangaPage` 데이터 클래스 정의
- **Scraper Service**:
    - `SpotvScraper`: Jsoup을 사용하여 각 카테고리별(완결, 인기, 최신, 추천 100) 리스트 파싱
    - 작품 상세 정보 및 회차별 이미지 URL 리스트 추출 로직 구현
    - 검색 기능 구현

### 3. 도메인 및 UI 레이어 (Jetpack Compose)
- **Navigation**: `Home`, `MangaList`, `MangaDetail`, `Viewer` 경로 설정
- **Screens**:
    - `HomeScreen`: 카테고리 선택 (완결, 인기, 최신, 추천 100) 및 검색 바
    - `MangaListScreen`: 작품 리스트를 그리드 형태로 표시
    - `MangaDetailScreen`: 작품 정보 및 회차 리스트 표시
    - **`ViewerScreen`**: 만화 보기 핵심 기능
        - **전체 화면 모드**
        - **한 장 보기 (Single)**
        - **두 장 엮어 보기 (Spread)**: Pager에서 한 번에 두 개의 이미지를 렌더링
        - **한 장 나누어 보기 (Split)**: 단일 이미지의 절반씩 잘라서 두 페이지로 처리

### 4. 크롤링 방지 우회 전략
- `OkHttp Interceptor`를 통해 모든 요청에 실제 모바일 기기의 `User-Agent` 주입
- 이미지 요청 시 `Referer` 헤더를 웹사이트 주소로 설정하여 외부 링크 방지 우회

---

## Verification Plan

### Automated Tests
- `SpotvScraper` 유닛 테스트: 실제 URL을 통해 HTML 구조가 올바르게 파싱되는지 확인 (네트워크 연결 필요)
- 데이터 모델 매핑 테스트

### Manual Verification
- 카테고리별 리스트 로딩 확인
- 작품 검색 기능 동작 확인
- 뷰어 모드 전환 (Single, Spread, Split) 동작 확인
- 이미지 로딩 및 캐싱 성능 확인

---
마지막으로, 에뮬레이터에서 앱을 구동하여 실제 웹 사이트의 데이터를 정상적으로 가져오는지 확인하겠습니다.
