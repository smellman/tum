package tum;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.s3.AmazonS3;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskManager
{
  static int count = 0;
  static final int threadNumber = 2000;
  static ExecutorService executor = Executors.newFixedThreadPool(2000);
  static List<Callable<String>> tasks = new ArrayList();
  static ClientConfiguration conf;
  static AmazonDynamoDB Dynamo;
  static AmazonKinesis Kinesis;
  static AmazonS3 S3;
  
  public static void tileSearchMain(String filePath, AmazonDynamoDB dynamo, AmazonKinesis kinesis, AmazonS3 s3)
  {
    Dynamo = dynamo;
    Kinesis = kinesis;
    S3 = s3;
    
    tileSearch(filePath);
    try
    {
      executor.invokeAll(tasks);
    }
    catch (InterruptedException e)
    {
      e.printStackTrace();
    }
  }
  
  public static void tileSearch(String filePath)
  {
    File file = new File(filePath);
    if (!file.isDirectory()) {
      file = file.getParentFile();
    }
    File[] arrayOfFile;
    int j = (arrayOfFile = file.listFiles()).length;
    for (int i = 0; i < j; i++)
    {
      File fc = arrayOfFile[i];
      if (fc.isDirectory())
      {
        tileSearch(fc.getPath());
      }
      else
      {
        fc.getPath();
        String csv = "csv";
        if (!fc.getPath().matches(".*" + csv + ".*"))
        {
          count += 1;
          addTaskToList(fc.getPath());
        }
      }
    }
  }
  
  public static void addTaskToList(String filePath)
  {
    tasks.add(new parallelTasks(filePath));
    if (count % 1000 == 0)
    {
      try
      {
        executor.invokeAll(tasks);
      }
      catch (InterruptedException e)
      {
        e.printStackTrace();
      }
      tasks.clear();
    }
  }
  
  static class parallelTasks
    implements Callable<String>
  {
    int taskNumber;
    String str;
    
    public parallelTasks(String str)
    {
      this.str = str;
    }
    
    public String call()
      throws Exception
    {
      DynamoDB.dynamoMain(this.str, TaskManager.Dynamo, TaskManager.Kinesis, TaskManager.S3);
      return this.str;
    }
  }
}
