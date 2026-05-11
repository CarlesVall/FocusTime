package com.focustime.repository.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;

@Converter(autoApply = true)
public class LocalDateTimeStringConverter implements AttributeConverter<LocalDateTime, String> {
    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : LocalDateTime.parse(dbData);
    }
}
