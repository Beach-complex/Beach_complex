package com.beachcheck.global.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.support.tracing.TracingTestConfiguration;
import javax.sql.DataSource;
import net.ttddyy.observation.boot.autoconfigure.JdbcProperties;
import net.ttddyy.observation.boot.autoconfigure.JdbcProperties.TraceType;
import net.ttddyy.observation.boot.autoconfigure.opentelemetry.JdbcOpenTelemetryProperties;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryMeterObservationHandler;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryQueryObservationConvention;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Import(TracingTestConfiguration.class)
@DisplayName("JDBC Trace 자동 설정 계약")
class JdbcTracingAutoConfigurationTest extends IntegrationTest {

  @Autowired private ApplicationContext context;
  @Autowired private DataSource dataSource;
  @Autowired private JdbcProperties jdbcProperties;
  @Autowired private JdbcOpenTelemetryProperties openTelemetryProperties;

  @Test
  @DisplayName("DataSource를 자동으로 감싸고 QUERY span만 활성화한다")
  void autoConfiguration_wrapsDataSourceAndEnablesQuerySpansOnly() {
    // Given
    JdbcOpenTelemetryProperties.Analysis analysis = openTelemetryProperties.getAnalysis();

    // When
    boolean proxied = AopUtils.isAopProxy(dataSource);

    // Then
    assertThat(proxied).isTrue();
    assertThat(jdbcProperties.getIncludes()).containsExactly(TraceType.QUERY);
    assertThat(jdbcProperties.getDatasourceProxy().isIncludeParameterValues()).isFalse();
    assertThat(jdbcProperties.getDatasourceProxy().getQuery().isEnableLogging()).isFalse();
    assertThat(analysis.getSanitize().isEnabled()).isTrue();
    assertThat(analysis.getSummary().isEnabled()).isTrue();
    assertThat(context.getBeansOfType(OpenTelemetryQueryObservationConvention.class)).hasSize(1);
    assertThat(context.getBeansOfType(QueryTracingObservationHandler.class))
        .hasSize(1)
        .allSatisfy(
            (name, handler) ->
                assertThat(handler).isInstanceOf(SanitizingQueryTracingObservationHandler.class));
    assertThat(context.getBeansOfType(OpenTelemetryMeterObservationHandler.class)).isEmpty();
  }
}
