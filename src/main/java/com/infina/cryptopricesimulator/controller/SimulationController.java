package com.infina.cryptopricesimulator.controller;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Simülasyon ile ilgili HTTP isteklerini karşılayan REST Controller'dır.
 * İş mantığı içermez. Gelen istekleri SimulationService katmanına iletir
 * ve sonucu istemciye döndürür.
 */
@RestController
@RequiredArgsConstructor
public class SimulationController {

}