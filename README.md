# BATCH
- root@c81f30ddbb71:/# psql -h  spring-3-batch-postgres-1 -U myuser -d mydatabase
- jobKey in batch_job_instance is the job parameter

## data source
link: https://www.kaggle.com/datasets/gregorut/videogamesales?resource=download

## Injecting Job Parameters
- see tasklet in Boot class
- steps
  - add @StepScope in order for the bean (Tasklet) to be recreated each time (test multiple time to validate)
    - then Inject using @Value
      - same way if the job parameters are the same for a period e.g same day job run, 
      - by default this would be seen used before, @StepScope helps job


## Reading and Writing
- Item Reader
  - contract for reader :->> read in one line at a tme
- Item Writer
  - contract for writer :->> you get a chunk

## DefaultBatchConfiguration
- this is called by default except you use @EnableBatchProcessing but you can't use together
- check JobLauncher Bean (uses SimpleAsyncTaskExecutor) --->>> it takes you to this Default class 
- same for JobRepository
- where 

