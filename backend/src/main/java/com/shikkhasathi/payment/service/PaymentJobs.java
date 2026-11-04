package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.jobs.JobHandler;
import com.shikkhasathi.common.jobs.JobNames;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** {@code payments.fulfil} and {@code payments.reconcile} job handlers. */
@Configuration
public class PaymentJobs {
  @Bean
  JobHandler<PaymentService.FulfilJob> paymentsFulfilJob(FulfilmentService fulfilment) {
    return new JobHandler<>() {
      @Override
      public String name() {
        return JobNames.PAYMENTS_FULFIL;
      }

      @Override
      public Class<PaymentService.FulfilJob> payloadType() {
        return PaymentService.FulfilJob.class;
      }

      @Override
      public void handle(PaymentService.FulfilJob p) {
        fulfilment.fulfil(p.orderId());
      }
    };
  }

  @Bean
  @SuppressWarnings("rawtypes")
  JobHandler<Map> paymentsReconcileJob(PaymentService payments) {
    return new JobHandler<>() {
      @Override
      public String name() {
        return JobNames.PAYMENTS_RECONCILE;
      }

      @Override
      public Class<Map> payloadType() {
        return Map.class;
      }

      @Override
      public void handle(Map p) {
        payments.reconcile();
      }

      @Override
      public int concurrency() {
        return 1;
      }
    };
  }
}
