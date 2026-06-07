-- =====================================================================================
-- V4 — storedProcedureJob 용 PL/pgSQL 함수.
--   APPROVED 거래를 refcursor 로 돌려준다. StoredProcedureItemReader 가 .function() +
--   refCursorPosition(1) 로 이 커서를 한 행씩 읽는다.
--   ⚠️ refcursor 는 트랜잭션 안에서만 유효 — 청크 스텝의 트랜잭션 경계 안에서 호출/소비된다.
-- =====================================================================================
CREATE OR REPLACE FUNCTION get_approved_txn()
    RETURNS refcursor
    LANGUAGE plpgsql
AS $$
DECLARE
    ref refcursor;
BEGIN
    OPEN ref FOR
        SELECT id, merchant_id, amount, status
        FROM txn
        WHERE status = 'APPROVED'
        ORDER BY id;
    RETURN ref;
END;
$$;
