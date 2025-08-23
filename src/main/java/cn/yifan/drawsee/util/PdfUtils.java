package cn.yifan.drawsee.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

public class PdfUtils {

	public static class PageText {
		public final int pageNo;
		public final String text;
		public PageText(int pageNo, String text) {
			this.pageNo = pageNo;
			this.text = text;
		}
	}

	public static List<BufferedImage> renderPages(InputStream inputStream, int dpi, IntPredicate pageFilter) throws IOException {
		byte[] bytes = toBytes(inputStream);
		try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
			PDFRenderer renderer = new PDFRenderer(document);
			int pageCount = document.getNumberOfPages();
			List<BufferedImage> images = new ArrayList<>();
			for (int p = 0; p < pageCount; p++) {
				if (pageFilter != null && !pageFilter.test(p)) continue;
				BufferedImage image = renderer.renderImageWithDPI(p, dpi);
				images.add(image);
			}
			return images;
		}
	}

	public static List<BufferedImage> renderFirstNPages(InputStream inputStream, int dpi, int maxPages) throws IOException {
		return renderPages(inputStream, dpi, idx -> idx < maxPages);
	}

	public static List<PageText> extractPageTexts(InputStream inputStream) throws IOException {
		byte[] bytes = toBytes(inputStream);
		try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
			int pageCount = document.getNumberOfPages();
			List<PageText> result = new ArrayList<>(pageCount);
			PDFTextStripper stripper = new PDFTextStripper();
			for (int p = 0; p < pageCount; p++) {
				stripper.setStartPage(p + 1);
				stripper.setEndPage(p + 1);
				String text = stripper.getText(document);
				result.add(new PageText(p + 1, cleanNoise(text)));
			}
			return result;
		}
	}

	public static String extractAllText(InputStream inputStream) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (PageText pt : extractPageTexts(inputStream)) {
			sb.append("\n\n[Page ").append(pt.pageNo).append("]\n").append(pt.text);
		}
		return sb.toString();
	}

	public static List<Integer> selectTopComplexPages(InputStream inputStream, int dpi, int topK) throws IOException {
		byte[] bytes = toBytes(inputStream);
		try (PDDocument document = PDDocument.load(new ByteArrayInputStream(bytes))) {
			PDFRenderer renderer = new PDFRenderer(document);
			int pageCount = document.getNumberOfPages();
			List<PageScore> scores = new ArrayList<>(pageCount);
			for (int p = 0; p < pageCount; p++) {
				BufferedImage img = renderer.renderImageWithDPI(p, dpi);
				double s = computeComplexityScore(img);
				scores.add(new PageScore(p, s));
			}
			scores.sort(Comparator.comparingDouble((PageScore ps) -> ps.score).reversed());
			List<Integer> top = new ArrayList<>();
			for (int i = 0; i < Math.min(topK, scores.size()); i++) {
				top.add(scores.get(i).pageIndex);
			}
			return top;
		}
	}

	private static class PageScore {
		int pageIndex;
		double score;
		PageScore(int pageIndex, double score) { this.pageIndex = pageIndex; this.score = score; }
	}

	private static double computeComplexityScore(BufferedImage img) {
		long sum = 0;
		long sumSq = 0;
		int w = img.getWidth();
		int h = img.getHeight();
		int stepX = Math.max(1, w / 400);
		int stepY = Math.max(1, h / 400);
		int count = 0;
		for (int y = 0; y < h; y += stepY) {
			for (int x = 0; x < w; x += stepX) {
				int rgb = img.getRGB(x, y);
				int r = (rgb >> 16) & 0xFF;
				int g = (rgb >> 8) & 0xFF;
				int b = (rgb) & 0xFF;
				int gray = (r * 299 + g * 587 + b * 114) / 1000;
				sum += gray;
				sumSq += (long) gray * gray;
				count++;
			}
		}
		double mean = sum / (double) count;
		double var = sumSq / (double) count - mean * mean;
		return var;
	}

	public static List<String> splitByMaxLength(String text, int maxLen) {
		List<String> parts = new ArrayList<>();
		int i = 0;
		while (i < text.length()) {
			int end = Math.min(text.length(), i + maxLen);
			parts.add(text.substring(i, end));
			i = end;
		}
		return parts;
	}

	private static byte[] toBytes(InputStream in) throws IOException {
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			int len;
			while ((len = in.read(buf)) != -1) {
				baos.write(buf, 0, len);
			}
			return baos.toByteArray();
		}
	}

	private static String cleanNoise(String text) {
		if (text == null) return "";
		String t = text.replaceAll("(?m)^\n+", "").replace('\r', '\n');
		return t.trim();
	}
} 