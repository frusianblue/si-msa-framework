package com.company.framework.filebatch.ops;

import com.company.framework.filebatch.BatchFileOperation;
import com.company.framework.filebatch.BatchItem;
import com.company.framework.filebatch.BatchSafety;
import com.company.framework.image.ImageProcessor;
import com.company.framework.image.ResizeSpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 이미지 변환 작업 — 실제 처리는 {@code framework-image} 의 {@link ImageProcessor} 에 위임(리사이즈/썸네일/포맷 +
 * EXIF orientation 보정 + 민감 메타데이터 제거). framework-image 가 클래스패스에 없으면 이 작업 빈은 등록되지 않는다
 * ({@code @ConditionalOnClass} 백오프).
 *
 * <p>경로가 있으면 변환 결과를 같은 디렉토리에 새 확장자로 기록하고, 없으면 결과 바이트를 본문으로 가진 아이템을 반환한다.
 * 이미지는 전체 디코드가 불가피하므로 파일 단위 메모리 적재는 허용한다(파일별로만).
 */
public final class ImageTransformOperation implements BatchFileOperation {

    private final ImageProcessor processor;
    private final ResizeSpec spec;

    public ImageTransformOperation(ImageProcessor processor, ResizeSpec spec) {
        this.processor = Objects.requireNonNull(processor, "processor");
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    @Override
    public String plan(BatchItem item) {
        return item.name() + " -> " + retarget(item.name()) + " (" + spec.maxWidth() + "x" + spec.maxHeight() + " "
                + spec.format() + ")";
    }

    @Override
    public BatchItem apply(BatchItem item) throws IOException {
        byte[] source;
        try (InputStream in = item.openContent()) {
            source = in.readAllBytes();
        }
        byte[] out = processor.process(source, spec);
        String target = retarget(item.name());
        if (item.sourcePath() != null) {
            Path dst = item.sourcePath().resolveSibling(target);
            Files.write(dst, out);
            return item.withSourcePath(dst).withName(target).withContent(() -> new ByteArrayInputStream(out));
        }
        return item.withName(target).withContent(() -> new ByteArrayInputStream(out));
    }

    private String retarget(String name) {
        return BatchSafety.requireSimpleName(
                RenameOperation.stem(name) + "." + spec.format().extension());
    }
}
