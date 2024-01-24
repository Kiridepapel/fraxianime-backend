package xyz.kiridepapel.fraxianimebackend.controller;

import java.util.HashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.java.Log;
import xyz.kiridepapel.fraxianimebackend.dto.AnimeInfoDTO;
import xyz.kiridepapel.fraxianimebackend.dto.HomePageDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.LinkDTO;
import xyz.kiridepapel.fraxianimebackend.dto.ChapterDTO;
import xyz.kiridepapel.fraxianimebackend.service.AnimeJkAnimeService;
import xyz.kiridepapel.fraxianimebackend.service.HomePageService;
import xyz.kiridepapel.fraxianimebackend.utils.AnimeUtils;
import xyz.kiridepapel.fraxianimebackend.service.ChapterAnimeLifeService;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = { "https://fraxianime.vercel.app", "http://localhost:4200" }, allowedHeaders = "**")
@Log
public class JKAnimeController {
  @Value("${PROVEEDOR_JKANIME_URL}")
  private String proveedorJkAnimeUrl;
  @Value("${PROVEEDOR_ANIMELIFE_URL}")
  private String proveedorAnimeLifeUrl;
  @Autowired
  private HomePageService homePageService;
  @Autowired
  private AnimeJkAnimeService animeService;
  @Autowired
  private ChapterAnimeLifeService chapterService;
  
  @GetMapping("/test")
  public ResponseEntity<?> test() {
    try {
      Document document = Jsoup.connect("https://jkanime.org/one-piece/").get();
      Element element = document.body().select(".contenido").first();

      AnimeInfoDTO animeInfo = this.animeService.getAnimeInfo(document);

      return new ResponseEntity<>(animeInfo, HttpStatus.OK);
    } catch (Exception e) {
      return new ResponseEntity<>("Ocurrió un error: " + e.getMessage(), HttpStatus.valueOf(500));
    }
  }
  
  @GetMapping("/animes")
  public ResponseEntity<?> homePage() {
    try {
      Document jkanime = Jsoup.connect(proveedorJkAnimeUrl).get();
      Document animeLife = Jsoup.connect(proveedorAnimeLifeUrl).get();

      HomePageDTO animes = HomePageDTO.builder()
        .sliderAnimes(this.homePageService.sliderAnimes(jkanime))
        .ovasOnasSpecials(this.homePageService.ovasOnasSpecials(jkanime))
        .animesProgramming(this.homePageService.animesProgramming(animeLife, jkanime))
        .donghuasProgramming(this.homePageService.donghuasProgramming(jkanime))
        .topAnimes(this.homePageService.topAnimes(jkanime))
        .latestAddedAnimes(this.homePageService.latestAddedAnimes(jkanime))
        .latestAddedList(this.homePageService.latestAddedList(jkanime))
        .build();
      
      if (
        AnimeUtils.isNotNullOrEmpty(animes.getSliderAnimes()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getOvasOnasSpecials()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getAnimesProgramming()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getDonghuasProgramming()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getTopAnimes()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getLatestAddedAnimes()) &&
        AnimeUtils.isNotNullOrEmpty(animes.getLatestAddedList())
      ) {
        return new ResponseEntity<>(animes, HttpStatus.OK);
      } else {
        return new ResponseEntity<>("Ocurrió un error al recuperar los datos.", HttpStatus.OK);
      }

    } catch (Exception e) {
      return new ResponseEntity<>("Ocurrió un error: " + e.getMessage(), HttpStatus.valueOf(500));
    }
  }

  @GetMapping("/{search}")
  public ResponseEntity<?> animeInfo(@PathVariable("search") String search) {
    log.info("search: " + this.proveedorAnimeLifeUrl + "anime/" +  search);
    try {
      Document document = Jsoup.connect(this.proveedorAnimeLifeUrl + "anime/" +  search).get();

      AnimeInfoDTO animeInfo = this.animeService.getAnimeInfo(document);
      
      return new ResponseEntity<>(animeInfo, HttpStatus.OK);
    } catch (Exception e) {
      return new ResponseEntity<>("Ocurrió un error: " + e.getMessage(), HttpStatus.valueOf(500));
    }
  }

  @GetMapping("/{name}/{chapter}")
  public ResponseEntity<?> chapter(
    @PathVariable("name") String name,
    @PathVariable("chapter") String chapter) {
    
    ChapterDTO animeInfo = this.chapterService.chapter(name, chapter);
    
    return new ResponseEntity<>(animeInfo, HttpStatus.OK);
  }

}
