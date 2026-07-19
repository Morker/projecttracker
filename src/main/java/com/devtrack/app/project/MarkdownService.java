package com.devtrack.app.project;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownService {

    private Parser parser;
    private HtmlRenderer renderer;
    private final Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);

    @PostConstruct
    public void init() {
        var extensions = List.of(
            TablesExtension.create(),
            StrikethroughExtension.create()
        );
        parser = Parser.builder()
            .extensions(extensions)
            .build();
        renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .build();
    }

    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return renderer.render(parser.parse(markdown));
    }

    public List<Heading> extractHeadings(String markdown) {
        List<Heading> headings = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) return headings;
        Matcher matcher = headingPattern.matcher(markdown);
        while (matcher.find()) {
            int level = matcher.group(1).length();
            String text = matcher.group(2).trim();
            String id = text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-+$", "");
            headings.add(new Heading(level, text, id));
        }
        return headings;
    }

    public record Heading(int level, String text, String id) {}
}
