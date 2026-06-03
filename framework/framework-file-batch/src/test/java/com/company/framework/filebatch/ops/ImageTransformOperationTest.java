package com.company.framework.filebatch.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.filebatch.BatchItem;
import com.company.framework.filebatch.BatchOptions;
import com.company.framework.filebatch.BatchResult;
import com.company.framework.filebatch.FileBatchProcessor;
import com.company.framework.image.ImageFormat;
import com.company.framework.image.ImageInfo;
import com.company.framework.image.ImageProcessor;
import com.company.framework.image.ResizeSpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 이미지 변환 위임 작업 검증 — 실제 인코딩은 {@code framework-image} 책임이므로 여기서는 <b>모의 ImageProcessor</b>
 * 로 일괄처리 측 책임만 본다: (1) 위임 호출, (2) 출력 포맷에 맞춘 확장자 재지정(retarget), (3) 경로/본문 분기.
 */
class ImageTransformOperationTest {

    private final FileBatchProcessor processor = new FileBatchProcessor();

    /** 입력 바이트를 받아 "PROCESSED:<len>" 를 돌려주는 모의 처리기(호출 횟수 기록). */
    private static final class MockProcessor implements ImageProcessor {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public byte[] process(byte[] source, ResizeSpec spec) {
            calls.incrementAndGet();
            return ("PROCESSED:" + source.length).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] thumbnail(byte[] source, int maxEdge) {
            return process(source, null);
        }

        @Override
        public ImageInfo probe(byte[] source) {
            return null;
        }
    }

    @Test
    @DisplayName("경로 기반: 출력 포맷(JPEG)에 맞춰 photo.png -> photo.jpg 로 재지정하고 처리 결과를 기록한다")
    void transformsPathRetargetingExtension(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("photo.png");
        Files.write(png, new byte[] {1, 2, 3, 4, 5});
        MockProcessor mock = new MockProcessor();
        ImageTransformOperation op = new BatchImageOps(mock).thumbnail(128, ImageFormat.JPEG, 0.8f);

        BatchResult r = processor.run(List.of(BatchItem.of(png)), op);

        assertThat(mock.calls.get()).isEqualTo(1);
        BatchItem out = r.outcomes().get(0).result();
        assertThat(out.name()).isEqualTo("photo.jpg");
        assertThat(Files.exists(dir.resolve("photo.jpg"))).isTrue();
        assertThat(Files.readString(dir.resolve("photo.jpg"))).isEqualTo("PROCESSED:5");
    }

    @Test
    @DisplayName("본문 기반(경로 없음): 처리 결과 바이트를 본문으로 반환하고 확장자를 재지정한다")
    void transformsContentToBytes() throws Exception {
        MockProcessor mock = new MockProcessor();
        ResizeSpec spec =
                ResizeSpec.builder().maxEdge(64).format(ImageFormat.PNG).build();
        ImageTransformOperation op = new BatchImageOps(mock).transform(spec);

        BatchResult r = processor.run(List.of(BatchItem.of("avatar.jpeg", new byte[] {9, 9, 9})), op);

        BatchItem out = r.outcomes().get(0).result();
        assertThat(out.name()).isEqualTo("avatar.png");
        assertThat(out.sourcePath()).isNull();
        try (InputStream in = out.openContent()) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("PROCESSED:3");
        }
    }

    @Test
    @DisplayName("드라이런: 처리기를 호출하지 않고 계획(대상 이름/명세)만 산출한다")
    void dryRunDoesNotInvokeProcessor(@TempDir Path dir) throws Exception {
        Path png = dir.resolve("keep.png");
        Files.write(png, new byte[] {1});
        MockProcessor mock = new MockProcessor();
        ImageTransformOperation op = new BatchImageOps(mock).thumbnail(128, ImageFormat.JPEG, 0.8f);

        BatchResult r = processor.run(
                List.of(BatchItem.of(png)), op, BatchOptions.defaults().withDryRun(true));

        assertThat(mock.calls.get()).isZero();
        assertThat(r.outcomes().get(0).message()).contains("keep.jpg");
        assertThat(Files.exists(dir.resolve("keep.jpg"))).isFalse();
    }

    @Test
    @DisplayName("여러 이미지를 병렬 변환 — 입력 순서로 결과가 모이고 각 확장자가 재지정된다")
    void transformsManyInOrder(@TempDir Path dir) throws Exception {
        var items = new java.util.ArrayList<BatchItem>();
        for (int i = 0; i < 5; i++) {
            Path f = dir.resolve("img" + i + ".png");
            Files.write(f, new byte[] {(byte) i});
            items.add(BatchItem.of(f));
        }
        ImageTransformOperation op = new BatchImageOps(new MockProcessor()).thumbnail(64, ImageFormat.JPEG, 0.9f);

        BatchResult r = processor.run(items, op);

        assertThat(r.succeeded()).isEqualTo(5);
        assertThat(r.outcomes().stream().map(o -> o.result().name()).toList())
                .containsExactly("img0.jpg", "img1.jpg", "img2.jpg", "img3.jpg", "img4.jpg");
    }
}
