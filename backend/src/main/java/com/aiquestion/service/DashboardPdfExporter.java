package com.aiquestion.service;

import java.awt.Color;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/** Printable report built from the same snapshot as the dashboard. No web or storage calls. */
public final class DashboardPdfExporter {
  private DashboardPdfExporter() {}
  public static byte[] write(DashboardService.Report report) throws IOException {
    try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream();
        var fontStream = DashboardPdfExporter.class.getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
      if (fontStream == null) throw new IOException("报告字体不可用");
      var font = PDType0Font.load(document, fontStream, true);
      var layout = new Layout(document, font, report);
      try { layout.render(); } finally { layout.close(); }
      document.getDocumentInformation().setTitle(report.personal() ? "个人培训报告" : "企业培训报告");
      document.getDocumentInformation().setCreator("e-learning");
      document.save(bytes);
      return bytes.toByteArray();
    }
  }

  private static final class Layout implements AutoCloseable {
    static final float W=PDRectangle.A4.getWidth(), H=PDRectangle.A4.getHeight(), M=42, CW=W-2*M;
    static final Color INK=new Color(24,63,53), GREEN=new Color(40,133,108), MUTED=new Color(105,123,118),
        LIGHT=new Color(242,247,245), LINE=new Color(222,232,227), AMBER=new Color(209,136,35);
    final PDDocument doc;
    final PDType0Font font;
    final DashboardService.Report report;
    PDPageContentStream out;
    float y;
    Layout(PDDocument doc, PDType0Font font, DashboardService.Report report) {this.doc=doc;this.font=font;this.report=report;}
    void page(String section) throws IOException {
      close();
      var page=new PDPage(PDRectangle.A4);doc.addPage(page);
      out=new PDPageContentStream(doc,page);
      rect(0,0,W,7,GREEN);
      text("e-learning  /  培训与考核",M,31,9,MUTED);
      text(section,W-180,31,9,MUTED);
      line(M,45,W-M,45,LINE);
      y=70;
    }
    void render() throws IOException {
      var data=report.data();var o=data.overview();
      page("01  数据总览");
      text(report.personal()?"个人培训报告":"企业培训报告",M,y,26,INK);y+=37;
      String date=Instant.parse(data.updatedAt()).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      text("数据生成时间："+date+"（北京时间）",M,y,9,MUTED);y+=23;
      for(String scope:report.scope()) paragraph(scope,10,MUTED,15);
      y+=17;
      String[][] cards={
        {"培训数量",o.trainingCount()+" 场","当前筛选范围"},
        {"应完成"+(report.personal()?"课程":"人次"),o.assigned()+(report.personal()?" 场":" 人次"),"按培训分配名单统计"},
        {"培训完成率",percent(o.completionRate()),"已完成 "+o.completed()+" / 应完成 "+o.assigned()},
        {"有效学习时长",DurationText.hours(o.studiedHours()),"累计有效学习时间"},
        {"考试通过率",percent(o.passRate()),"通过 "+o.passed()+" / 应考 "+o.examAssigned()},
        {"逾期未完成率",o.assigned()==0?"—":percent(o.overdue()*100.0/o.assigned()),"逾期未完成 "+o.overdue()+" / 应完成 "+o.assigned()}
      };
      ensure(430);
      float gap=10,cw=(CW-2*gap)/3,top=y;
      for(int i=0;i<cards.length;i++) {
        float x=M+(i%3)*(cw+gap),t=top+(i/3)*102;
        rect(x,t,cw,91,LIGHT);text(cards[i][0],x+12,t+20,10,MUTED);
        float size=fit(cards[i][1],cw-24,20);
        text(cards[i][1],x+12,t+49,size,i==5?AMBER:INK);
        text(cards[i][2],x+12,t+74,8,MUTED);
      }
      y=top+220;section("培训完成分布");
      long[] counts={o.completed(),o.inProgress(),o.notStarted()};String[] names={"已完成","进行中","未开始"};Color[] colors={GREEN,AMBER,new Color(174,189,184)};
      if(o.assigned()==0) paragraph("暂无培训名单。无统计分母的比例显示为 —。",10,MUTED,17);
      else for(int i=0;i<3;i++) {
        text(names[i],M,y+12,10,INK);rect(M+66,y,CW-190,16,LIGHT);
        rect(M+66,y,(CW-190)*counts[i]/o.assigned(),16,colors[i]);
        text(counts[i]+" / "+percent(counts[i]*100.0/o.assigned()),W-M-110,y+12,10,INK);y+=32;
      }
      y+=12;paragraph("阅读说明：本报告按当前筛选条件汇总，包含全部匹配培训，不受网页分页影响。",9,MUTED,15);

      page("02  趋势与部门");section("近 30 天培训完成趋势");
      paragraph("按完成日期统计完成人次；日期采用北京时间。",10,MUTED,17);y+=10;
      var trend=data.trend();float topChart=y,chartH=135,chartW=CW-30,left=M+25;
      double max=Math.max(2,Math.ceil(trend.stream().mapToDouble(r->num(r,"count")).max().orElse(0)/2)*2);
      for(int i=0;i<3;i++) {float yy=topChart+chartH-chartH*i/2;line(left,yy,W-M,yy,LINE);text(String.format(Locale.ROOT,"%.0f",max*i/2),M,yy+3,8,MUTED);}
      if(!trend.isEmpty()) {
        float step=chartW/trend.size();
        for(int i=0;i<trend.size();i++) {float h=(float)(chartH*num(trend.get(i),"count")/max);rect(left+i*step+2,topChart+chartH-h,Math.max(1,step-4),h,GREEN);}
        text(str(trend.get(0),"date"),left,topChart+chartH+18,8,MUTED);
        text(str(trend.get(trend.size()-1),"date"),W-M-65,topChart+chartH+18,8,MUTED);
      }
      y=topChart+chartH+40;
      if(trend.stream().noneMatch(r->num(r,"count")>0)) paragraph("最近 30 天暂无完成记录。",10,MUTED,17);
      y+=18;section("部门完成情况");
      paragraph("按培训分配时的部门统计，学员调岗不会改写历史归属。",9,MUTED,16);y+=8;
      table(new String[]{"部门","应完成人次","已完成人次","完成率"},new float[]{CW-240,80,80,80},
        data.departments().stream().map(r->new String[]{str(r,"name"),integer(r,"assigned"),integer(r,"completed"),percent((Number)r.get("rate"))}).toList());

      page("03  培训汇总");section("全部匹配培训");
      paragraph("共 "+data.total()+" 场培训。有效学习时长是累计值，不代表课程标准时长。",9,MUTED,16);y+=10;
      table(new String[]{"培训名称","应完成","已完成","完成率","逾期","有效学习时长"},new float[]{CW-322,47,47,56,42,130},
        data.rows().stream().map(r->new String[]{str(r,"name"),integer(r,"assigned"),integer(r,"completed"),percent((Number)r.get("completionRate")),integer(r,"overdue"),DurationText.hours(num(r,"studiedHours"))}).toList());
      ensure(225);y+=20;section("统计口径");
      for(String note:List.of(
        "培训时间：按培训开始日期筛选；未设置开始日期时按创建日期。",
        "应完成人次：每位学员每场培训计 1 人次。个人报告中对应应完成课程数。",
        "培训完成：完成学习且通过关联考试；无考试时只看学习。完成率 = 已完成 ÷ 应完成。",
        "考试通过率：通过人次 ÷ 应考人次，按最近一次考试记录判断；未考和待批阅计入分母。",
        "逾期未完成：培训学习逾期或考试逾期未通过，每人每场去重；待批阅不算考试逾期。",
        report.personal() ? "逾期未完成率 = 逾期未完成课程数 ÷ 应完成课程数。" : "逾期未完成率 = 逾期未完成人次 ÷ 应完成人次。",
        "进行中：已有学习或答题记录但尚未完成；没有记录计为未开始。",
        "有效学习时长：累计视频和材料的有效学习时间，按时、分、秒显示。",
        "本报告仅包含筛选范围内的数据。没有统计分母时比例显示为 —，不表示 0%。")) paragraph(note,9,MUTED,15);
      close();
      for(int i=0;i<doc.getNumberOfPages();i++) {
        out=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true,true);
        line(M,H-40,W-M,H-40,LINE);text("e-learning  ·  培训统计报告",M,H-24,8,MUTED);
        text((i+1)+" / "+doc.getNumberOfPages(),W-M-40,H-24,8,MUTED);close();
      }
    }
    void section(String title) throws IOException {ensure(45);rect(M,y-12,3,18,GREEN);text(title,M+12,y+2,15,INK);y+=25;}
    void ensure(float height) throws IOException {if(y+height>H-60) page("统计报告 / 续页");}
    void paragraph(String value,float size,Color color,float leading) throws IOException {
      for(String part:wrap(value,CW,size)) {ensure(leading);text(part,M,y,size,color);y+=leading;}
    }
    void table(String[] header,float[] widths,List<String[]> rows) throws IOException {
      tableHeader(header,widths);
      if(rows.isEmpty()) {paragraph("暂无符合筛选条件的数据。",10,MUTED,20);return;}
      int n=0;
      for(String[] row:rows) {
        var cells=new ArrayList<List<String>>();int lines=1;
        for(int i=0;i<row.length;i++) {var cell=wrap(row[i],widths[i]-14,9);cells.add(cell);lines=Math.max(lines,cell.size());}
        // Stream unusually long names over continuation pages without clipping.
        int offset=0;
        while(offset<lines) {
          if(y+30>H-60) {page("培训统计 / 续页");tableHeader(header,widths);}
          int chunk=Math.min(lines-offset,Math.max(1,(int)((H-60-y-14)/14)));
          float height=chunk*14+14,x=M;
          if(n%2==0) rect(M,y,CW,height,LIGHT);
          for(int i=0;i<cells.size();i++) {
            for(int j=0;j<chunk && offset+j<cells.get(i).size();j++) text(cells.get(i).get(offset+j),x+7,y+17+j*14,9,INK);
            x+=widths[i];
          }
          y+=height;line(M,y,W-M,y,LINE);offset+=chunk;
        }
        n++;
      }
      y+=12;
    }
    void tableHeader(String[] names,float[] widths) throws IOException {
      ensure(40);rect(M,y,CW,28,INK);float x=M;
      for(int i=0;i<names.length;i++){text(names[i],x+7,y+18,9,Color.WHITE);x+=widths[i];}
      y+=28;
    }
    List<String> wrap(String value,float width,float size) throws IOException {
      String clean=clean(value);var result=new ArrayList<String>();var line=new StringBuilder();float used=0;
      for(int cp:clean.codePoints().toArray()) {
        String c=new String(Character.toChars(cp));float cw=font.getStringWidth(c)/1000*size;
        if(used+cw>width && !line.isEmpty()){result.add(line.toString());line.setLength(0);used=0;}
        line.append(c);used+=cw;
      }
      if(!line.isEmpty()||result.isEmpty())result.add(line.toString());return result;
    }
    String clean(String value) throws IOException {
      var result=new StringBuilder();
      for(int cp:Objects.toString(value,"").codePoints().toArray()) {
        if(Character.isISOControl(cp)){result.append(' ');continue;}
        String c=new String(Character.toChars(cp));
        try {font.encode(c);result.append(c);}catch(IllegalArgumentException e){result.append('?');}
      }
      return result.toString();
    }
    float fit(String value,float width,float max) throws IOException {return Math.min(max,width*1000/Math.max(1,font.getStringWidth(clean(value))));}
    void text(String value,float x,float top,float size,Color color) throws IOException {
      out.beginText();out.setFont(font,size);out.setNonStrokingColor(color);out.newLineAtOffset(x,H-top);out.showText(clean(value));out.endText();
    }
    void rect(float x,float top,float w,float h,Color color) throws IOException {if(w<=0||h<=0)return;out.setNonStrokingColor(color);out.addRect(x,H-top-h,w,h);out.fill();}
    void line(float x,float top,float end,float bottom,Color color) throws IOException {out.setStrokingColor(color);out.setLineWidth(.5f);out.moveTo(x,H-top);out.lineTo(end,H-bottom);out.stroke();}
    @Override public void close() throws IOException {if(out!=null){out.close();out=null;}}
    static String str(Map<String,Object> row,String key){return Objects.toString(row.get(key),"—");}
    static double num(Map<String,Object> row,String key){return row.get(key) instanceof Number n?n.doubleValue():0;}
    static String integer(Map<String,Object> row,String key){return Long.toString((long)num(row,key));}
    static String percent(Number value){return value==null?"—":String.format(Locale.ROOT,"%.1f%%",value.doubleValue());}
  }
}
