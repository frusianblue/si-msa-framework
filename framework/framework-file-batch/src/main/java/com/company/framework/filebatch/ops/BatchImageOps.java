package com.company.framework.filebatch.ops;

import com.company.framework.image.ImageFormat;
import com.company.framework.image.ImageProcessor;
import com.company.framework.image.ResizeSpec;
import java.util.Objects;

/**
 * 이미지 변환 작업 팩토리(상태 없는 싱글톤). 주입된 {@link ImageProcessor} 를 들고 있다가 호출별 명세로
 * {@link ImageTransformOperation} 인스턴스를 만든다(명세는 호출마다 다르므로 작업 자체를 빈으로 두지 않는다).
 *
 * <p>framework-image 가 있을 때만 오토컨피그가 이 빈을 등록한다({@code @ConditionalOnClass}/{@code @ConditionalOnBean}).
 */
public final class BatchImageOps {

    private final ImageProcessor processor;

    public BatchImageOps(ImageProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor");
    }

    /** 임의 명세로 변환 작업 생성. */
    public ImageTransformOperation transform(ResizeSpec spec) {
        return new ImageTransformOperation(processor, spec);
    }

    /** 정사각 박스 썸네일 변환 작업 생성. */
    public ImageTransformOperation thumbnail(int maxEdge, ImageFormat format, float quality) {
        return new ImageTransformOperation(processor, ResizeSpec.thumbnail(maxEdge, format, quality));
    }
}
