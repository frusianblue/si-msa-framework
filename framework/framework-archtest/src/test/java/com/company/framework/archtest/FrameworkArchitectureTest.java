package com.company.framework.archtest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 프레임워크 전체 아키텍처 규칙 강제(테스트 전용). 모든 라이브러리 모듈의 main 바이트코드를
 * 임포트해 com.company.framework 패키지에 대해 아래 규칙을 검증한다(테스트 클래스는 제외).
 *
 * <p>받는 쪽: {@code ./gradlew :framework:framework-archtest:test} 로 그린 확인.
 */
@AnalyzeClasses(packages = "com.company.framework", importOptions = ImportOption.DoNotIncludeTests.class)
public class FrameworkArchitectureTest {

    /** 모듈(첫 패키지 세그먼트=core/security/client/...) 간 순환 의존 금지. */
    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            slices().matching("com.company.framework.(*)..").should().beFreeOfCycles();

    /**
     * Jackson 3 만 사용: tools.jackson.* 허용. 3에서 이동된 패키지 의존 금지.
     * jackson-annotations(com.fasterxml.jackson.annotation)는 3에서도 com.fasterxml 에 유지되므로 제외.
     */
    @ArchTest
    static final ArchRule no_jackson2_moved_packages = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.fasterxml.jackson.databind..",
                    "com.fasterxml.jackson.core..",
                    "com.fasterxml.jackson.dataformat..",
                    "com.fasterxml.jackson.datatype..",
                    "com.fasterxml.jackson.module..")
            .because("Jackson 3 규약: tools.jackson.* 만 허용(이동된 com.fasterxml.jackson.* 금지). "
                    + "단 jackson-annotations 는 3에서도 유지되므로 com.fasterxml.jackson.annotation 은 허용.");

    /** 영속 계층(mapper)은 웹/서비스 계층에 의존하면 안 된다. */
    @ArchTest
    static final ArchRule mappers_do_not_depend_on_web_or_service = noClasses()
            .that()
            .resideInAPackage("..mapper..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..web..", "..service..")
            .because("매퍼(영속)는 상위 계층(web/service)에 의존하면 안 된다.")
            .allowEmptyShould(true);

    /** 도메인 모델은 상위 계층(web/service)·영속(mapper)에 의존하면 안 된다. */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_upper_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..web..", "..service..", "..mapper..")
            .because("도메인 모델은 web/service/mapper 에 의존하면 안 된다.")
            .allowEmptyShould(true);

    /** 자동구성 클래스는 @AutoConfiguration 이어야 한다. */
    @ArchTest
    static final ArchRule autoconfiguration_classes_are_annotated = classes()
            .that()
            .haveSimpleNameEndingWith("AutoConfiguration")
            .should()
            .beAnnotatedWith(AutoConfiguration.class)
            .because("자동구성 클래스(*AutoConfiguration)는 @AutoConfiguration 이어야 한다.")
            .allowEmptyShould(true);

    /** top-level 설정 프로퍼티 클래스는 @ConfigurationProperties 여야 한다(중첩 설정 그룹 제외). */
    @ArchTest
    static final ArchRule properties_classes_are_annotated = classes()
            .that()
            .haveSimpleNameEndingWith("Properties")
            .and()
            .areTopLevelClasses()
            .should()
            .beAnnotatedWith(ConfigurationProperties.class)
            .because("설정 프로퍼티 클래스(top-level *Properties)는 @ConfigurationProperties 여야 한다.")
            .allowEmptyShould(true);

    /** 필드 주입 금지(생성자 주입 강제). */
    @ArchTest
    static final ArchRule no_field_injection = NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    /**
     * 보안-영속 결합 분리: framework-security <b>코어</b>는 특정 영속 기술(MyBatis)에 결합되면 안 된다.
     * RBAC 영속은 어댑터(framework-security-rbac-mybatis)로 분리됐다. 어댑터 패키지
     * ({@code ..rbac.mybatis..})와 어댑터가 소유한 매퍼 패키지({@code ..rbac.mapper.. — FQN 유지})는 예외.
     */
    @ArchTest
    static final ArchRule security_core_does_not_depend_on_mybatis = noClasses()
            .that()
            .resideInAPackage("com.company.framework.security..")
            .and()
            .resideOutsideOfPackages(
                    "com.company.framework.security.rbac.mybatis..", "com.company.framework.security.rbac.mapper..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.company.framework.mybatis..", "org.mybatis..", "org.apache.ibatis..")
            .because("framework-security 코어는 MyBatis 에 결합되면 안 된다 — RBAC 영속은 어댑터(framework-security-rbac-mybatis)로 분리.")
            .allowEmptyShould(true);

    /** RBAC 포트(SPI)는 MyBatis 타입을 시그니처/구현에 노출하면 안 된다(영속 기술 중립). */
    @ArchTest
    static final ArchRule rbac_spi_ports_are_persistence_neutral = noClasses()
            .that()
            .resideInAPackage("com.company.framework.security.rbac.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.company.framework.mybatis..", "org.mybatis..", "org.apache.ibatis..")
            .because("RBAC 포트(SPI)는 특정 영속 기술(MyBatis)에 중립이어야 한다 — 도메인 타입만 시그니처에 노출.")
            .allowEmptyShould(true);
}
