package tum;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public class DynamoDB
{
  static final long kinesisLimit = 1000000L;
  static final String bucketName = "input your bucket name";
  static AmazonDynamoDB Dynamo;
  static AmazonKinesis KinesisClient;
  static AmazonS3 S3Client;

  public static void dynamoMain(String filePath, AmazonDynamoDB dynamo, AmazonKinesis kinesis, AmazonS3 s3)
  {
    Dynamo = dynamo;
    KinesisClient = kinesis;
    S3Client = s3;

    File file = new File(filePath);

    String temp = filePath.substring(0, filePath.length() -
      file.getName().length());

    int index = temp.indexOf("xyz");
    temp = temp.substring(index).trim();

    String spl = "\\\\";
    int s = 0;

    String[] saved = temp.split(spl);

    String x = saved[(saved.length - 1)];
    String z = saved[(saved.length - 2)];

    s = x.length() + z.length();

    String t = temp.substring(4, temp.length() - s - 3);

    int point = file.getName().lastIndexOf(".");
    String y = file.getName().substring(0, point);
    String ext = file.getName().substring(point + 1);
    String coordinate = z + "/" + x + "/" + y;
    String destination = "xyz/" + t + "/" + z + "/" + x + "/";

    String md5sum = calcMD5sum(filePath);

    Map<String, AttributeValue> item = makeNewItem(destination + y, md5sum,
      t, ext, coordinate);
    if (putToDynamoDB(item, Dynamo)) {
      if (file.length() > 1000000L) {
        S3.putToS3(destination, filePath, S3Client);
      } else {
        Kinesis.kinesisMain(filePath, destination, KinesisClient);
      }
    }
  }

  public static String calcMD5sum(String filePath)
  {
    String ret = "";
    try
    {
      StringBuilder sb = new StringBuilder();
      MessageDigest md = MessageDigest.getInstance("MD5");

      DigestInputStream inStream = new DigestInputStream(
        new BufferedInputStream(new FileInputStream(filePath)), md);
      while (inStream.read() != -1) {}
      byte[] digest = md.digest();
      inStream.close();
      for (int i = 0; i < digest.length; i++) {
        sb.append(String.format("%02x", new Object[] { Byte.valueOf(digest[i]) }));
      }
      ret = new String(sb);
    }
    catch (NoSuchAlgorithmException e)
    {
      e.printStackTrace();
    }
    catch (FileNotFoundException e)
    {
      e.printStackTrace();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    return ret;
  }

  private static Map<String, AttributeValue> makeNewItem(String tileInfo, String md5sum, String type, String ext, String coordinate)
  {
    Map<String, AttributeValue> item = new HashMap();

    item.put("tileInfo", new AttributeValue(tileInfo));
    item.put("md5sum", new AttributeValue(md5sum));
    item.put("type", new AttributeValue(type));
    item.put("ext", new AttributeValue(ext));
    item.put("coordinate", new AttributeValue(coordinate));

    return item;
  }

  public static boolean putToDynamoDB(Map<String, AttributeValue> item, AmazonDynamoDB dynamo2)
  {
    AmazonDynamoDB Dynamo = dynamo2;
    try
    {
      Region Reg = Region.getRegion(Regions.AP_NORTHEAST_1);
      Dynamo.setRegion(Reg);

      String tableName = "input your tablename";

      PutItemRequest putItemRequest = new PutItemRequest(tableName, item)
        .addExpectedEntry("tileInfo",
        new ExpectedAttributeValue().withExists(Boolean.valueOf(false)))
        .addExpectedEntry("md5sum",
        new ExpectedAttributeValue().withExists(Boolean.valueOf(false)));
      PutItemResult putItemResult = Dynamo.putItem(putItemRequest);

      return true;
    }
    catch (ConditionalCheckFailedException a) {}
    return false;
  }

  public static String makeMokuroku(ClientConfiguration configure, String type, String path)
    throws IOException
  {
    String mokurokuPath = path + "/mokuroku.csv";
    File file = new File(mokurokuPath);

    PrintWriter pw = new PrintWriter(new BufferedWriter(
      new FileWriter(file)));

    String preFix = type;

    AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();

    AmazonS3 s3 = new AmazonS3Client(credentialsProvider, configure);

    ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
      .withBucketName(input your bucketname).withPrefix(preFix);

    ListObjectsRequest request = new ListObjectsRequest("input your bucketname", preFix,
      null, "/", null);

    int index = 0;
    ObjectListing objectListing;
    do
    {
      objectListing = s3.listObjects(listObjectsRequest);

      Iterator localIterator = objectListing.getObjectSummaries().iterator();
      while (localIterator.hasNext())
      {
        S3ObjectSummary objectSummary = (S3ObjectSummary)localIterator.next();

        String temp = objectSummary.getKey().substring(type.length()).trim() +
          "," + objectSummary.getSize() + "," +
          objectSummary.getLastModified().getTime() + "," +
          objectSummary.getETag();

        System.out.println(temp);
        pw.println(temp);
      }
      listObjectsRequest.setMarker(objectListing.getNextMarker());
    } while (objectListing.isTruncated());
    pw.close();

    return mokurokuPath;
  }

  public static List<String> getTypeList(ClientConfiguration configure)
  {
    String preFix = "xyz/";

    AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();

    AmazonS3 s3 = new AmazonS3Client(credentialsProvider, configure);

    ListObjectsRequest request = new ListObjectsRequest(input your bucketname, preFix,
      null, "/", null);

    ObjectListing objectListing = s3.listObjects(request);
    List<String> folderList = objectListing.getCommonPrefixes();

    return folderList;
  }

  public static String compressMokuroku(String type, String mokurokuPath)
    throws IOException
  {
    byte[] buf = new byte['?'];
    String from = mokurokuPath;
    String to = mokurokuPath + ".gz";

    BufferedInputStream in = new BufferedInputStream(new FileInputStream(
      from));

    GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(to));
    int size;
    while ((size = in.read(buf, 0, buf.length)) != -1)
    {
      int size;
      out.write(buf, 0, size);
    }
    out.flush();
    out.close();
    in.close();

    return to;
  }

  public static void mokuroku(String path, ClientConfiguration configure)
    throws IOException
  {
    AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();

    AmazonS3 s3 = new AmazonS3Client(credentialsProvider, configure);

    List<String> typeList = getTypeList(configure);
    for (String type : typeList)
    {
      String mokurokuPath = makeMokuroku(configure, type, path);

      String mokurokuGzPath = compressMokuroku(type, mokurokuPath);
      File putFile = new File(mokurokuGzPath);

      System.out.println("put:" + type + putFile.getName());

      s3.putObject(new PutObjectRequest(input your bucketname, path +
        putFile.getName(), putFile));

      putFile.delete();
    }
  }

  public static List<String> getCoordinateList(ClientConfiguration configure)
  {
    AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();

    AmazonDynamoDB Dynamo = new AmazonDynamoDBClient(credentialsProvider, configure);

    Region Reg = Region.getRegion(Regions.AP_NORTHEAST_1);
    Dynamo.setRegion(Reg);

    System.out.print(Dynamo.listTables());
    String tableName = input your table name;

    String val = "std";

    Condition scanFilterCondition = new Condition().withComparisonOperator(
      ComparisonOperator.EQ.toString()).withAttributeValueList(new AttributeValue[] {
      new AttributeValue().withS(val) });

    Map<String, Condition> conditions = new HashMap();
    conditions.put("type", scanFilterCondition);

    ScanRequest scanRequest = new ScanRequest().withTableName(tableName)
      .withScanFilter(conditions);

    ScanResult result = Dynamo.scan(scanRequest);

    int i = 0;
    List<String> CoordinateList = new ArrayList();
    do
    {
      if (result != null) {
        scanRequest.setExclusiveStartKey(result.getLastEvaluatedKey());
      }
      result = Dynamo.scan(scanRequest);
      for (Map<String, AttributeValue> item : result.getItems())
      {
        CoordinateList.add(((AttributeValue)item.get("coordinate")).getS());
        System.out.println(((AttributeValue)item.get("coordinate")).getS());
      }
      try
      {
        Thread.sleep(2000L);
      }
      catch (InterruptedException e)
      {
        e.printStackTrace();
      }
    } while (result.getLastEvaluatedKey() != null);
    return CoordinateList;
  }

  public static void cocotile(String path, ClientConfiguration configure)
    throws IOException
  {
    AWSCredentialsProvider credentialsProvider = new ClasspathPropertiesFileCredentialsProvider();

    AmazonS3 s3 = new AmazonS3Client(credentialsProvider, configure);
    AmazonDynamoDB Dynamo = new AmazonDynamoDBClient(credentialsProvider, configure);

    Region Reg = Region.getRegion(Regions.AP_NORTHEAST_1);
    Dynamo.setRegion(Reg);

    System.out.print(Dynamo.listTables());
    String tableName = "input your table name";

    List<String> coordinateList = getCoordinateList(configure);
    for (String coordinate : coordinateList)
    {
      Condition scanFilterCondition = new Condition()
        .withComparisonOperator(ComparisonOperator.EQ.toString())
        .withAttributeValueList(new AttributeValue[] {
        new AttributeValue().withS(coordinate) });

      Map<String, Condition> conditions = new HashMap();
      conditions.put("coordinate", scanFilterCondition);

      ScanRequest scanRequest = new ScanRequest()
        .withTableName(tableName).withScanFilter(conditions);

      ScanResult result = Dynamo.scan(scanRequest);
      String outputType = "";
      do
      {
        if (result != null) {
          scanRequest.setExclusiveStartKey(result
            .getLastEvaluatedKey());
        }
        for (Map<String, AttributeValue> item : result.getItems()) {
          outputType = outputType + ((AttributeValue)item.get("type")).getS() + ",";
        }
        try
        {
          Thread.sleep(3000L);
        }
        catch (InterruptedException e)
        {
          e.printStackTrace();
        }
        result = Dynamo.scan(scanRequest);
      } while (result.getLastEvaluatedKey() != null);
      outputType = outputType.substring(0, outputType.length() - 1);

      String cocotilePath = coordinate + ".csv";

      File file = new File(cocotilePath);
      PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(
        file)));
      pw.print(outputType);
      pw.close();

      String[] strs = coordinate.split("/");

      String s3Path = "xyz/cocotile/" + strs[0] + "/" + strs[1];

      s3.putObject(new PutObjectRequest(input your bucketname, s3Path +
        file.getName(), file));
      System.out.println("put:" + s3Path + file.getName());
    }
  }
}
