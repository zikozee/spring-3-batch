package com.zee.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.lang.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SpringBootApplication
public class Spring3BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(Spring3BatchApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(JobLauncher jobLauncher, Job job){
        return args -> {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("uuid", UUID.randomUUID().toString())
                    .toJobParameters();
            JobExecution jobExec = jobLauncher.run(job, jobParameters);
            System.out.println("job status: " + jobExec.getStatus() + ",  instance Id: " + jobExec.getJobInstance().getInstanceId());
            System.out.println("job id: " + jobExec.getJobId() + ", creation time: " + jobExec.getStartTime());

//            jobLauncher.run(job, new JobParameters(Map.of("uuid", new JobParameter<>(UUID.randomUUID().toString(), String.class))));
        };
    }

    @Bean
    @StepScope
    Tasklet tasklet(@Value("#{jobParameters['uuid']}") String uuid) {
        System.out.println("hello, world! the UUID is " + uuid) ;
        return (contribution, chunkContext) -> RepeatStatus.FINISHED;
    }

    @Bean
    Step step(JobRepository jobRepository, Tasklet tasklet, PlatformTransactionManager tx) {
        return new StepBuilder("step1", jobRepository)
                .tasklet(tasklet, tx)
                .build();
    }

    @Bean
//    @StepScope  // for new initialized more like Prototype to be recreated each time
    Job job(JobRepository jobRepository, Step step) {
        return new JobBuilder("job", jobRepository)
                .start(step)
                .build();
    }

}

//@SwitchComponent
//class MyListener implements ApplicationListener<ApplicationReadyEvent> {
//
//    @Override
//    public void onApplicationEvent(ApplicationReadyEvent event) {
//        System.out.println("Hello, World!!! from a custom component");
//    }
//}
//
//
//@Target({ElementType.TYPE})
//@Retention(RetentionPolicy.RUNTIME)
//@Documented
//@Component
//@interface SwitchComponent {
//    @AliasFor(
//            annotation = Component.class
//    )
//    String value() default "";
//}

