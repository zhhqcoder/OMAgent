import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Paragraph;
import java.io.*;
import java.util.*;

public class AnalyzeDoc {
    public static void main(String[] args) throws Exception {
        FileInputStream fis = new FileInputStream("d:/IdeaProjects/OMAgent/uploads/0f27fca3-dcb3-4eb6-8a0c-1a4bc8e9dc2e_OMS配置说明书.doc");
        HWPFDocument doc = new HWPFDocument(fis);
        Range range = doc.getRange();

        StringBuilder sb = new StringBuilder();
        int tableRowCount = 0;
        int headingCount = 0;
        int totalParagraphs = 0;
        List<String> headings = new ArrayList<>();
        List<String> tableRows = new ArrayList<>();

        for (int i = 0; i < range.numParagraphs(); i++) {
            Paragraph p = range.getParagraph(i);
            String text = p.text();
            if (text == null || text.trim().isEmpty()) continue;

            text = text.replace("\u0007", "\t").replace("\r", "").replaceAll("\t{2,}", "\t").trim();
            if (text.isEmpty()) continue;

            totalParagraphs++;

            // 检测是否为标题（包含制表符的可能是表格行）
            if (text.contains("\t")) {
                tableRowCount++;
                tableRows.add(text);
            } else {
                // 非表格段落，可能是标题或正文
                if (text.length() < 80 && (text.matches("^[0-9]+[\\.．].*") || text.matches("^[一二三四五六七八九十]+[、．.].*") || text.matches("^第[一二三四五六七八九十]+[章节].*"))) {
                    headingCount++;
                    headings.add(text);
                }
                sb.append(text).append("\n");
            }
        }

        System.out.println("=== 文档统计 ===");
        System.out.println("总段落数: " + totalParagraphs);
        System.out.println("表格行数: " + tableRowCount);
        System.out.println("标题数: " + headingCount);
        System.out.println("总文本长度: " + sb.length());
        
        System.out.println("\n=== 标题列表 ===");
        for (String h : headings) {
            System.out.println("  " + h);
        }

        System.out.println("\n=== 表格内容样例(前30行) ===");
        for (int i = 0; i < Math.min(30, tableRows.size()); i++) {
            System.out.println("  [" + i + "] " + tableRows.get(i));
        }

        System.out.println("\n=== 前3000字 ===");
        String allText = sb.toString();
        System.out.println(allText.substring(0, Math.min(3000, allText.length())));

        fis.close();
    }
}
