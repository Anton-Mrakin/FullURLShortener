package com.mrakin.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Url implements Serializable {
    private static final long serialVersionUID = 1L;
    private String originalUrl;
    private String shortCode;
}
