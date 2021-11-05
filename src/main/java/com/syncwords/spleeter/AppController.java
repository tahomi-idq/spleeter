package com.syncwords.spleeter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@Slf4j
@RestController
public class AppController {

    @Autowired
    TmpFolder tmpFolder;

    String html = "<!DOCTYPE html>\n" +
            "<html lang=\"en\">\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>Title</title>\n" +
            "</head>\n" +
            "<body>\n" +
            "    <form action=\"file\" method=\"post\" enctype=\"multipart/form-data\">\n" +
            "       <p>duration in sec: <input type=\"text\" name=\"duration\" /></p>\n" +
            "       <p>document:<input type=\"file\" name=\"file\"/>\n" +
            "            <input type=\"submit\" value=\"upload\" />\n" +
            "            <input type=\"reset\" value=\"Reset\" /></p>\n" +
            "    </form>\n" +
            "\n" +
            "\n" +
            "</body>\n" +
            "</html>";

    @RequestMapping("/")
    public String website() throws IOException {
        log.info("Opened page");
        return html;
    }

    @PostMapping(
            value = "file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/zip"
    )
    public ResponseEntity<byte[]> processAudio(@RequestPart MultipartFile file, @RequestParam String duration) throws IOException, InterruptedException {
        log.info("Got file process request");
        Path folder = Path.of(tmpFolder.getTmpFolderName());
        SpleeterService spleeterService = new SpleeterService(folder);// Todo: work with
        byte[] zip = spleeterService.processFile(file, Integer.parseInt(duration));
        return ResponseEntity
                .status(200)
                .body(zip);
    }
}
