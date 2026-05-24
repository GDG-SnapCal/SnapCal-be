package com.snapcal.snapcalbackend.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

@Component
public class ExifExtractor {

    public Optional<LocalDate> extract(byte[] imageBytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
            ExifSubIFDDirectory directory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (directory != null) {
                Date date = directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    return Optional.of(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
                }
            }
        } catch (Exception ignored) {
            // EXIF 없는 이미지는 빈 Optional 반환
        }
        return Optional.empty();
    }
}
