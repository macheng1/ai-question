package com.aiquestion.integration;

import com.aiquestion.config.*;
import com.aiquestion.storage.ObjectStorage;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageClientReuseTest {
  @ParameterizedTest @ValueSource(booleans={true,false})
  void realSdkReusesHttpConnectionAndCanReadAfterPartialClose(boolean eds) throws Exception {
    var ports=new java.util.concurrent.CopyOnWriteArrayList<Integer>();
    byte[] body=new byte[32768];
    var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      ports.add(exchange.getRemoteAddress().getPort());exchange.getRequestBody().readAllBytes();
      if("GET".equals(exchange.getRequestMethod())) {
        exchange.getResponseHeaders().set("Content-Type","application/octet-stream");
        exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
      } else { exchange.sendResponseHeaders(204,-1); }
      exchange.close();
    });
    server.start();
    String endpoint="http://127.0.0.1:"+server.getAddress().getPort();
    ObjectStorage storage=eds?new EdsStorageService(new EdsProperties(endpoint,"bucket","key","secret",""))
        :new AliyunOssService(new AliyunOssProperties("key","secret",endpoint,"bucket",""));
    try {
      for(int i=0;i<3;i++)try(var input=storage.open("sample").stream()){assertEquals(body.length,input.readAllBytes().length);}
      assertEquals(1,new java.util.HashSet<>(ports).size(),"Completed reads should reuse the TCP connection");
      try(var input=storage.open("sample").stream()){assertEquals(0,input.read());}
      try(var input=storage.open("sample").stream()){assertEquals(body.length,input.readAllBytes().length);}
      storage.delete("sample");
    } finally {((AutoCloseable)storage).close();server.stop(0);}
  }

  @ParameterizedTest @ValueSource(booleans={true,false})
  void unusedProviderCanCloseWithoutConfigurationOrInitialization(boolean eds) throws Exception {
    if(eds) {
      var storage=spy(new EdsStorageService(new EdsProperties("","","","","")));
      storage.close();storage.close();verify(storage,never()).createClient();
    } else {
      var storage=spy(new AliyunOssService(new AliyunOssProperties("","","","","")));
      storage.close();storage.close();verify(storage,never()).createClient();
    }
  }

  @ParameterizedTest @ValueSource(booleans={true,false})
  void concurrentOperationsReuseOneClientAndClosingOneStreamDoesNotCloseOthers(boolean eds) throws Exception {
    var created=new AtomicInteger();
    var first=spy(new ByteArrayInputStream(new byte[]{1,2,3}));
    var second=spy(new ByteArrayInputStream(new byte[]{4,5,6}));
    ObjectStorage storage;
    var s3=mock(com.amazonaws.services.s3.AmazonS3.class);
    var oss=mock(com.aliyun.oss.OSS.class);
    if(eds) {
      storage=new EdsStorageService(new EdsProperties("http://localhost","bucket","key","secret","")) {
        @Override protected com.amazonaws.services.s3.AmazonS3 createClient(){created.incrementAndGet();return s3;}
      };
      var a=new com.amazonaws.services.s3.model.S3Object();a.setObjectContent(first);a.getObjectMetadata().setContentLength(3);
      var b=new com.amazonaws.services.s3.model.S3Object();b.setObjectContent(second);b.getObjectMetadata().setContentLength(3);
      when(s3.getObject("bucket","a")).thenReturn(a);when(s3.getObject("bucket","b")).thenReturn(b);
    } else {
      storage=new AliyunOssService(new AliyunOssProperties("key","secret","http://localhost","bucket","")) {
        @Override protected com.aliyun.oss.OSS createClient(){created.incrementAndGet();return oss;}
      };
      var a=new com.aliyun.oss.model.OSSObject();a.setObjectContent(first);a.getObjectMetadata().setContentLength(3);
      var b=new com.aliyun.oss.model.OSSObject();b.setObjectContent(second);b.getObjectMetadata().setContentLength(3);
      when(oss.getObject("bucket","a")).thenReturn(a);when(oss.getObject("bucket","b")).thenReturn(b);
    }
    var pool=Executors.newFixedThreadPool(8);
    try {
      var start=new CountDownLatch(1);var jobs=new java.util.ArrayList<Future<?>>();
      for(int i=0;i<24;i++)jobs.add(pool.submit(()->{try{start.await();storage.delete("unused");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new RuntimeException(e);}}));
      start.countDown();for(var job:jobs)job.get(5,TimeUnit.SECONDS);
      try(var a=storage.open("a").stream();var b=storage.open("b").stream()) {
        a.read();a.close();verify(first,atLeastOnce()).close();verify(second,never()).close();
        assertArrayEquals(new byte[]{4,5,6},b.readAllBytes());
      }
      verify(second).close();assertEquals(1,created.get());
      verify(s3,never()).shutdown();verify(oss,never()).shutdown();
      ((AutoCloseable)storage).close();((AutoCloseable)storage).close();
      if(eds)verify(s3,times(1)).shutdown();else verify(oss,times(1)).shutdown();
      assertThrows(IllegalStateException.class,()->storage.delete("after-close"));
      assertEquals(1,created.get(),"Closed providers must not silently rebuild their clients");
    } finally {pool.shutdownNow();((AutoCloseable)storage).close();}
  }
}
