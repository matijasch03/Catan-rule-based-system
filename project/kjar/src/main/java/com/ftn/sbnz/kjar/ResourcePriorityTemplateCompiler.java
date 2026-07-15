package com.ftn.sbnz.kjar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.drools.template.DataProviderCompiler;
import org.drools.template.objects.ArrayDataProvider;

public final class ResourcePriorityTemplateCompiler {

    private ResourcePriorityTemplateCompiler() {
    }

    public static String compile(InputStream template, InputStream data) throws IOException {
        if (template == null || data == null) {
            throw new IllegalArgumentException("Resource priority template and data are required.");
        }

        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(data, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                if (!line.isBlank()) {
                    rows.add(line.split(",", -1));
                }
            }
        }

        String[][] values = rows.toArray(String[][]::new);
        // Every column is mandatory in our data file, so optional-column wrappers
        // are unnecessary (and Drools 7.49's MVEL wrapper mishandles quoted tokens).
        return new DataProviderCompiler().compile(
                new ArrayDataProvider(values), template, false);
    }
}
