-- 이 서비스의 첫 마이그레이션 스크립트입니다.
-- 파일명을 V20__<서비스명>.sql 로 바꾸고 내용을 채웁니다.
-- V1 부터 V19 는 공통 모듈이 사용하는 대역이므로 쓰지 않습니다.
--
-- 이미 적용된 스크립트는 수정하지 않습니다.
-- 내용이 바뀌면 체크섬이 달라져 다음 기동이 실패합니다.
-- 변경이 필요하면 다음 번호로 새 스크립트를 만듭니다.

CREATE TABLE template
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,

    -- 아래 6개 컬럼은 모든 테이블이 공통으로 가집니다.
    -- 공통 모듈의 BaseEntity 와 짝을 이루므로 빠뜨리면 기동 검증에 실패합니다.
    created_at TIMESTAMP    NOT NULL,
    created_by VARCHAR(45)  NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR(45),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(45)
);
