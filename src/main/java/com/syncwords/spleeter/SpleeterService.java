package com.syncwords.spleeter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class SpleeterService {

    static final int maxTime = 9 * 60;
    Path tmpFolder;
    File tempDir;
    Path outputFolder;

    public SpleeterService(Path workFolder) throws IOException {
        tmpFolder = workFolder.resolve("tmp");
        tempDir = new File(tmpFolder.toString());
        if (Files.exists(tmpFolder)) {
            FileUtils.deleteDirectory(tmpFolder.toFile());
        }
        if ( ! tempDir.mkdirs()) {
            throw new RuntimeException("Temp folder hasn't created");
        }
        File outDir = new File(tempDir.toPath().resolve("output").toString());
        if (outDir.mkdirs()) {
            outputFolder = outDir.toPath();
        } else {
            throw new RuntimeException("Output folder hasn't created");
        }
    }

    public byte[] processFile(@NonNull MultipartFile file, @NonNull int duration)
            throws IOException, InterruptedException {
        TempAudio tempAudioFile = new TempAudio(file);
        log.info("Started processing file");

        if (duration > maxTime) {
            List<Path> fileParts = splitByTime(tempAudioFile, duration);
            Files.deleteIfExists(tempAudioFile.getPath());
            for (Path partAudio : fileParts) {
                splitAudioFile(partAudio);
                Files.deleteIfExists(partAudio);
            }

        } else {
            splitAudioFile(tempAudioFile.getPath());
            Files.deleteIfExists(tempAudioFile.getPath());
        }

        Path accompaniment = getPartsByIndexAndJoinToFile(0);
        Path vocals = getPartsByIndexAndJoinToFile(1);

        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        ZipOutputStream archiveOutputStream = new ZipOutputStream(zip);
        writeFilesToArchiveStream(archiveOutputStream, accompaniment, vocals);
        archiveOutputStream.close();

        FileUtils.deleteDirectory(tempDir);
        return zip.toByteArray();
    }

    private List<Path> splitByTime(TempAudio tempAudio, int duration) throws IOException, InterruptedException {
        List<Path> parts = new ArrayList<>();
        int id = 1;
        int start = 0;
        String startTime;
        String endTime = parseTime(0);
        while (start + maxTime < duration) {
            startTime = parseTime(start);
            endTime = parseTime(start + maxTime);
            parts.add(cutPart(tempAudio, startTime, endTime, id));
            id++;
            start += maxTime;
        }
        startTime = endTime;
        endTime = null;
        parts.add(cutPart(tempAudio, startTime, endTime, id));

        return parts;
    }

    private String parseTime(int time) {
        LocalTime localTime = LocalTime.ofSecondOfDay(time);
        String stringLocal = localTime.toString();
        if (stringLocal.length() != 5) {
            return stringLocal;
        } else {
            return stringLocal + ":00";
        }
    }

    private Path cutPart(TempAudio audio, String start, @Nullable String end, int id) throws IOException, InterruptedException {
        Path file = tmpFolder.resolve(id + "_part" + audio.extension);
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder
                .append("ffmpeg ")
                .append(" -i ")
                .append(audio.getPath().toAbsolutePath())
                .append(" -ss ")
                .append(start);
        if (end!=null) {
            commandBuilder
                    .append(" -to ")
                    .append(end);
        }

        commandBuilder
                .append(" -c copy -map_chapters -1 ")
                .append(file.toAbsolutePath());

        runCommand(commandBuilder.toString());
        return file;
    }

    private void runCommand(String cmd) throws IOException, InterruptedException {
        Runtime run = Runtime.getRuntime();
        Process pr = run.exec(cmd);
        pr.waitFor();
        BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        String line = "";
        while ((line=buf.readLine())!=null) {
            System.out.println(line);
        }
    }

    private void splitAudioFile(Path audioPath) throws IOException, InterruptedException {
        String cmd = "spleeter separate -p spleeter:2stems -o "
                + outputFolder.toAbsolutePath()
                + " "
                + audioPath.toAbsolutePath();
        runCommand(cmd);
    }

    private Path getPartsByIndexAndJoinToFile(int index) throws IOException, InterruptedException {
        String filename = "accompaniment.wav";
        if (index != 0) {
            filename = "vocals.wav";
        }
        Path result = tmpFolder.resolve(filename);
        Path list = tmpFolder.resolve("list.txt");
        StringBuilder fileListBuilder = new StringBuilder();
        for (File folder : Objects.requireNonNull(outputFolder.toFile().listFiles())) {
            log.info(folder.toString());
            Path audio = Objects.requireNonNull(folder.listFiles())[index].toPath();
            fileListBuilder
                    .append("file '")
                    .append(audio.toAbsolutePath())
                    .append("'")
                    .append("\n");
        }
        Files.write(list, fileListBuilder.toString().getBytes());
        String command = "ffmpeg -f concat -safe 0 -i " + list.toAbsolutePath() + " -c copy " + result.toAbsolutePath();
        System.out.println(command);
        runCommand(command);

        return result;
    }

    private void writeFilesToArchiveStream(ZipOutputStream stream, Path ... paths) throws IOException {
        for (Path splittedFile : paths){
            ZipEntry fileEntry = new ZipEntry(splittedFile.getFileName().toString());
            stream.putNextEntry(fileEntry);
            stream.write(Files.readAllBytes(splittedFile));
            stream.closeEntry();
        }
    }

    @Getter
    private class TempAudio{
        private final String extension;
        private final String filename;
        private final File audioFile;

        public TempAudio(MultipartFile file) throws IOException {
            filename = file.getOriginalFilename();
            assert filename != null;
            extension = filename.substring(filename.lastIndexOf("."));
            audioFile = new File(tempDir, "temp-media" + extension);
            if ( ! audioFile.createNewFile()) {
                throw new RuntimeException("File hasn't created");
            }
            InputStream inputStream = file.getInputStream();
            OutputStream outStream = new FileOutputStream(audioFile);
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outStream);
        }

        public Path getPath() {
            return audioFile.toPath();
        }
    }
}
