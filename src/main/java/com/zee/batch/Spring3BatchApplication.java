package com.zee.batch;

import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
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
//    @StepScope  // for new initialized more like Prototype to be recreated each time
    Job job(JobRepository jobRepository, CsvToDbStepConfiguration csvToDbStepConfiguration) {
        return new JobBuilder("job", jobRepository)
                .start(csvToDbStepConfiguration.csvToDb())
                .build();
    }




}

@Configuration

class CsvToDbStepConfiguration {

    private final DataSource datasource;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager tx;

    private final Resource resource;

    CsvToDbStepConfiguration(DataSource datasource, JobRepository jobRepository, PlatformTransactionManager tx,
                             //@Value("file:///C:/Users/zikoz/Desktop/JAVA/MAVEN/2024_PROJECTS/may/spring-3-batch/data/vgsales.csv")
                             @Value("classpath:vgsales.csv") Resource resource) {
        this.datasource = datasource;
        this.jobRepository = jobRepository;
        this.tx = tx;
        this.resource = resource;
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
    FlatFileItemReader<CsvRow> csvRowFlatFileItemReader() {
        return new FlatFileItemReaderBuilder<CsvRow>()
                .name("csvRowReader")
                .resource(resource)
                .delimited().delimiter(",")
                .names("rank,name,platform,year,genre,publisher,na_sales,eu_sales,jp_sales,other_sales,global_sales".split(","))
                .linesToSkip(1) //###STRIP HEADER
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
                        fieldSet.readFloat("global_sales") // we can use name too
                ))
                .build();
    }

    private static int parseIntText(String text) {
        if(text != null && !text.isEmpty() && !text.contains("N/A")) return Integer.parseInt(text);
        return 0;
    }



    @Bean
    JdbcBatchItemWriter<CsvRow> csvRowJdbcBatchItemWriter() {
        String sql = """
                    insert into video_game_sales(
                        rank,
                        name,
                        platform,
                        year,
                        genre,
                        publisher,
                        na_sales,
                        eu_sales,
                        jp_sales,
                        other_sales,
                        global_sales
                     )
                     values(
                        :rank,
                        :name,
                        :platform,
                        :year,
                        :genre,
                        :publisher,
                        :na_sales,
                        :eu_sales,
                        :jp_sales,
                        :other_sales,
                        :global_sales
                     )
                     ON CONFLICT on constraint video_game_sales_name_platform_year_genre_key  do update set
                        rank=excluded.rank,
                        na_sales=excluded.na_sales,
                        eu_sales=excluded.eu_sales,
                        jp_sales=excluded.jp_sales,
                        other_sales=excluded.other_sales,
                        global_sales=excluded.global_sales
                     ;
                """;
        return new JdbcBatchItemWriterBuilder<CsvRow>()
                .dataSource(datasource)
                .sql(sql)
                .itemSqlParameterSourceProvider(item -> {
                    Map<String, Object> map = new HashMap<>();
                    map.putAll(
                            Map.of(
                                    "rank", item.rank(),
                                    "name", item.name(),
                                    "platform", item.platform(),
                                    "year", item.year(),
                                    "genre", item.genre(),
                                    "publisher", item.publisher()
                            )
                    );

                    map.putAll(
                            Map.of(
                                    "na_sales", item.na(),
                                    "eu_sales", item.eu(),
                                    "jp_sales", item.jp(),
                                    "other_sales", item.other(),
                                    "global_sales", item.global()
                            )
                    );
                    return new MapSqlParameterSource(map);
                })
                .build();
    }


    @Bean
    Step csvToDb() {

//        String[] lines = (String[]) null;
//        try (InputStreamReader reader = new InputStreamReader(data.getInputStream())){
//            String string = FileCopyUtils.copyToString(reader);
//            lines = string.split("n");
//            System.out.println("there are " + lines.length + " rows");
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

        return new StepBuilder("csvToDb", jobRepository)
                .<CsvRow,CsvRow>chunk(100, tx)
//                .reader(new ListItemReader<>(Arrays.asList(lines)))
                .reader(csvRowFlatFileItemReader()) // spring caches this bean
//                .writer(new ItemWriter<CsvRow>() {
//
//                    @Override
//                    public void write(Chunk<? extends CsvRow> chunk) throws Exception {
//                        List<? extends CsvRow> onHundredRows = chunk.getItems();
//                        System.out.println("got " + onHundredRows.size());
//                    }
//                }).
                .writer(csvRowJdbcBatchItemWriter())  // spring caches this bean
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

