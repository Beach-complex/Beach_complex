package com.beachcheck.global.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.sql.SQLException;
import net.ttddyy.observation.tracing.DataSourceBaseContext;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Why: JDBC driver 예외 메시지에 포함될 수 있는 SQL bind 값을 Trace로 내보내지 않는다.
 *
 * <p>Policy: 원래 예외는 업무 호출자에게 그대로 전파하고, span에는 제한된 오류 유형과 일반화된 메시지만 기록한다.
 *
 * <p>Contract(Input): datasource-micrometer가 생성한 QueryContext
 *
 * <p>Contract(Output): ERROR span에는 원래 예외 클래스명과 민감정보가 제거된 exception event만 기록
 */
@Component
@Order(MicrometerTracingAutoConfiguration.DEFAULT_TRACING_OBSERVATION_HANDLER_ORDER - 1000)
public class SanitizingQueryTracingObservationHandler extends QueryTracingObservationHandler {

  private static final String ERROR_TYPE = "error.type";
  private static final String SANITIZED_ERROR_MESSAGE = "JDBC 쿼리 실행 실패";

  public SanitizingQueryTracingObservationHandler(Tracer tracer) {
    super(tracer);
  }

  @Override
  public void onError(DataSourceBaseContext context) {
    Throwable error = context.getError();
    if (error == null) {
      return;
    }

    Span span = getRequiredSpan(context);
    span.tag(ERROR_TYPE, error.getClass().getName());
    span.error(new SQLException(SANITIZED_ERROR_MESSAGE));
  }
}
