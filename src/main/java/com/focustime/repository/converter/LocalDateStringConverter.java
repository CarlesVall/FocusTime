package com.focustime.repository.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter(autoApply = true)
public class LocalDateStringConverter implements AttributeConverter<LocalDate, String> {
    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : LocalDate.parse(dbData);
    }
}
