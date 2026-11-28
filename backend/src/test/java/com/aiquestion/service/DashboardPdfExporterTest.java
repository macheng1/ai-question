package com.aiquestion.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

class DashboardPdfExporterTest {
  @Test void reportContainsChineseExactDurationsAndAllRowsAcrossPages() throws Exception {
    var rows = new ArrayList<Map<String,Object>>();
    for (int i=1;i<=85;i++) rows.add(Map.of("name", "培训"+i+"：企业新员工学习与安全培训长名称验证", "assigned", 4L,
        "completed", 2L, "completionRate", 50.0, "overdue", 1L, "studiedHours", 90/3600.0));
    var overview = new DashboardService.Overview(85,340,170,85,85,25+90/3600.0,100,80,85,0,50.0,80.0);
    var data = new DashboardService.Result(overview, List.of(Map.of("name","研发部门","assigned",340L,"completed",170L,"rate",50.0)),
        List.of(Map.of("date","2026-11-27","count",2L)),rows,85,1,10,"2026-11-27T08:00:00Z");
    var report = new DashboardService.Report(data,List.of("培训时间：全部时间", "学员：张三 · 当前部门：研发部门"),true);
    byte[] bytes = DashboardPdfExporter.write(report);
    try (var pdf=Loader.loadPDF(bytes)) {
      String text = new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("个人培训报告"));
      assertTrue(text.contains("25小时1分30秒"));
      assertTrue(text.contains("培训85"));
      assertTrue(text.contains("统计口径"));
      assertTrue(pdf.getNumberOfPages()>3);
      for(var page:pdf.getPages()) assertTrue(page.getMediaBox().getWidth()<600);
    }
    Files.createDirectories(Path.of("target/report-preview"));
    Files.write(Path.of("target/report-preview/long-personal.pdf"),bytes);
  }

  @Test void emptyReportHasNoInventedRatesAndRemainsReadable() throws Exception {
    var data=new DashboardService.Result(new DashboardService.Overview(0,0,0,0,0,0,0,0,0,0,null,null),List.of(),List.of(),List.of(),0,1,10,"2026-11-27T08:00:00Z");
    byte[] bytes=DashboardPdfExporter.write(new DashboardService.Report(data,List.of("培训时间：全部时间"),false));
    try(var pdf=Loader.loadPDF(bytes)) {
      String text=new PDFTextStripper().getText(pdf);
      assertTrue(text.contains("暂无"));
      assertFalse(text.contains("NaN"));
      assertTrue(text.contains("企业培训报告"));
    }
    Files.createDirectories(Path.of("target/report-preview"));
    Files.write(Path.of("target/report-preview/empty.pdf"),bytes);
  }
}
