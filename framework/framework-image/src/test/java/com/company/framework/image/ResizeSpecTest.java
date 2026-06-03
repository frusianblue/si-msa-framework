package com.company.framework.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ResizeSpec 명세/검증")
class ResizeSpecTest {

    @Test
    @DisplayName("빌더 기본값: 1920 박스, 업스케일 금지, JPEG, 품질 0.85, 보정 on")
    void builderDefaults() {
        ResizeSpec s = ResizeSpec.builder().build();
        assertThat(s.maxWidth()).isEqualTo(1920);
        assertThat(s.maxHeight()).isEqualTo(1920);
        assertThat(s.allowUpscale()).isFalse();
        assertThat(s.format()).isEqualTo(ImageFormat.JPEG);
        assertThat(s.quality()).isEqualTo(0.85f);
        assertThat(s.correctOrientation()).isTrue();
    }

    @Test
    @DisplayName("thumbnail 팩토리: 정사각 박스")
    void thumbnailFactory() {
        ResizeSpec s = ResizeSpec.thumbnail(256, ImageFormat.PNG, 0.9f);
        assertThat(s.maxWidth()).isEqualTo(256);
        assertThat(s.maxHeight()).isEqualTo(256);
        assertThat(s.format()).isEqualTo(ImageFormat.PNG);
        assertThat(s.allowUpscale()).isFalse();
    }

    @Test
    @DisplayName("maxEdge 빌더는 가로/세로를 동시에 설정")
    void maxEdge() {
        ResizeSpec s = ResizeSpec.builder().maxEdge(100).build();
        assertThat(s.maxWidth()).isEqualTo(100);
        assertThat(s.maxHeight()).isEqualTo(100);
    }

    @Test
    @DisplayName("잘못된 값은 생성 시 거부")
    void rejectsInvalid() {
        assertThatThrownBy(() -> new ResizeSpec(0, 100, false, ImageFormat.JPEG, 0.8f, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(100, -1, false, ImageFormat.JPEG, 0.8f, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(100, 100, false, null, 0.8f, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(100, 100, false, ImageFormat.JPEG, 1.5f, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ResizeSpec(100, 100, false, ImageFormat.JPEG, -0.1f, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
