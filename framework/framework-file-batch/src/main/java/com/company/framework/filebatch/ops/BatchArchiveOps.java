package com.company.framework.filebatch.ops;

import com.company.framework.archive.Archiver;
import java.util.Objects;

/**
 * 압축 작업 팩토리(상태 없는 싱글톤). 주입된 {@link Archiver} 를 들고 있다가 {@link CompressOperation} 을 만든다.
 *
 * <p>framework-archive 가 있을 때만 오토컨피그가 이 빈을 등록한다({@code @ConditionalOnClass}/{@code @ConditionalOnBean}).
 */
public final class BatchArchiveOps {

    private final Archiver archiver;

    public BatchArchiveOps(Archiver archiver) {
        this.archiver = Objects.requireNonNull(archiver, "archiver");
    }

    /** 파일별 GZIP 압축 작업 생성. */
    public CompressOperation gzip() {
        return new CompressOperation(archiver);
    }
}
