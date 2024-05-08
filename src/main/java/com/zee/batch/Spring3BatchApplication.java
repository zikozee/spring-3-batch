package com.zee.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FieldSet;
import org.springframework.batch.item.support.ListItemReader;
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
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.FileCopyUtils;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.*;
import java.util.*;

@SpringBootApplication
public class Spring3BatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(Spring3BatchApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(JobLauncher jobLauncher, Job job){
        return args -> {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addDate("date", new Date())
                    .toJobParameters();
            JobExecution jobExec = jobLauncher.run(job, jobParameters);
            System.out.println("job status: " + jobExec.getStatus() + ",  instance Id: " + jobExec.getJobInstance().getInstanceId());
            System.out.println("job id: " + jobExec.getJobId() + ", creation time: " + jobExec.getStartTime());

//            jobLauncher.run(job, new JobParameters(Map.of("uuid", new JobParameter<>(UUID.randomUUID().toString(), String.class))));
        };
    }

    @Bean
    @StepScope
    Tasklet tasklet(@Value("#{jobParameters['date']}") String uuid) {
        System.out.println("hello, world! the date is " + uuid) ;
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
    Job job(JobRepository jobRepository, Step step, Step csvToDb) {
        return new JobBuilder("job", jobRepository)
                .start(step)
                .next(csvToDb)
                .build();
    }

    record CsvRow(
            int rank,
            String name,
            String platform,
            int year,
            String genre,
            String publisher,
            float na,
            float eu,
            float jp,
            float other,
            float global) {}

    @Bean
    FlatFileItemReader<CsvRow> csvRowFlatFileItemReader(
            @Value("file:///C:/Users/zikoz/Desktop/JAVA/MAVEN/2024_PROJECTS/may/spring-3-batch/data/vgsales.csv")
            Resource resource
    ) {
        return new FlatFileItemReaderBuilder<CsvRow>()
                .name("csvRowReader")
                .resource(resource)
                .delimited().delimiter(",")
                .names("rank,name,platform,year,genre,publisher,sales,eu,jp,other,global".split(","))
                .linesToSkip(1)
                .fieldSetMapper(fieldSet -> new CsvRow(
                        fieldSet.readInt("rank"), // we can use name too
                        fieldSet.readString(1),
                        fieldSet.readString(2),
                        parseIntText(fieldSet.readString(3)),
                        fieldSet.readString(4),
                        fieldSet.readString(5),
                        fieldSet.readFloat(6),
                        fieldSet.readFloat(7),
                        fieldSet.readFloat(8),
                        fieldSet.readFloat(9),
                        fieldSet.readFloat("global") // we can use name too
                ))
                .build();
    }

    private static int parseIntText(String text) {
        if(text != null && !text.isEmpty() && !text.contains("N/A")) return Integer.parseInt(text);
        return 0;
    }

    @Bean
    Step csvToDb(JobRepository jobRepository, PlatformTransactionManager tx,
                FlatFileItemReader<CsvRow> csvRowFlatFileItemReader) {

//        String[] lines = (String[]) null;
//        try (InputStreamReader reader = new InputStreamReader(data.getInputStream())){
//            String string = FileCopyUtils.copyToString(reader);
//            lines = string.split("n");
//            System.out.println("there are " + lines.length + " rows");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        return new StepBuilder("csvToDb", jobRepository)
                .<CsvRow, CsvRow>chunk(100, tx)
//                .reader(new ListItemReader<>(Arrays.asList(lines)))
                .reader(csvRowFlatFileItemReader)
                .writer(new ItemWriter<CsvRow>() {

                    @Override
                    public void write(Chunk<? extends CsvRow> chunk) throws Exception {
                        List<? extends CsvRow> onHundredRows = chunk.getItems();
                        System.out.println("got " + onHundredRows.size());
                    }
                }).faultTolerant()
//                .skip(FlatFileParseException.class)
//                .skip(NumberFormatException.class)
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

