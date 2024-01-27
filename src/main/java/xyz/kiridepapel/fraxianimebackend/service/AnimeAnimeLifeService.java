package xyz.kiridepapel.fraxianimebackend.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.extern.java.Log;
import xyz.kiridepapel.fraxianimebackend.exception.AnimeExceptions.AnimeNotFound;
import xyz.kiridepapel.fraxianimebackend.utils.AnimeUtils;
import xyz.kiridepapel.fraxianimebackend.utils.DataUtils;
import xyz.kiridepapel.fraxianimebackend.dto.AnimeInfoDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.LinkDTO;

@Service
@Log
public class AnimeAnimeLifeService {
  @Value("${PROVIDER_JKANIME_URL}")
  private String providerJkanimeUrl;
  @Value("${PROVIDER_ANIMELIFE_URL}")
  private String providerAnimeLifeUrl;

  @Autowired
  private AnimeJkAnimeService jkAnimeService;

  @Autowired
  private AnimeUtils animeUtils;

  private Map<String, String> specialKeys = Map.ofEntries(
    Map.entry("Publicado el", "emited"),
    Map.entry("Publicado", "emited"),
    Map.entry("Duracion", "duration"),
    Map.entry("Tipo", "type"),
    Map.entry("Director", "director"),
    Map.entry("Censura", "censured"),
    Map.entry("Reparto", "cast"),
    Map.entry("Actualizado el", "lastUpdate"),
    Map.entry("Año", "year"),
    Map.entry("Estado", "status"),
    Map.entry("Episodios", "chapters"),
    Map.entry("Estudio", "studio")
  );
  
  @Cacheable("anime")
  public AnimeInfoDTO animeInfo(String search) {
    try {
      Document docJkanime = DataUtils.tryConnectOrReturnNull((this.providerJkanimeUrl + search), 1);
      Document docAnimeLife = DataUtils.tryConnectOrReturnNull((this.providerAnimeLifeUrl + "anime/" + search), 2);

      Element mainAnimeLife = docAnimeLife.body().select(".wrapper").first();

      if (mainAnimeLife == null) {
        throw new AnimeNotFound("Contenido del anime no disponible.");
      }

      String trailer = mainAnimeLife.select(".trailerbutton").attr("href").replace("watch?v=", "embed/");
      if (trailer.isEmpty()) {
        trailer = null;
      }

      AnimeInfoDTO animeInfo = AnimeInfoDTO.builder()
        .name(this.animeUtils.specialNameOrUrlCases(mainAnimeLife.select(".entry-title").text().trim(), 'n'))
        .alternativeName(mainAnimeLife.select(".entry-title").text().trim())
        .imgUrl(mainAnimeLife.select(".thumbook img").attr("src").trim())
        .sinopsis(mainAnimeLife.select(".synp p").text().trim())
        .trailer(trailer)
        .data(this.getAnimeData(docAnimeLife))
        // .recomendations(this.getRecomendations(docAnimeLife));
        .build();
      
      // Traer la información de los capítulos
      animeInfo = this.setFirstAndLastChapters(animeInfo, docAnimeLife);

      // Si el capitulo existe en el proveedor 1, modificar la infomación con la de este
      if (docJkanime != null) {
        animeInfo = this.jkAnimeService.getAnimeInfo(animeInfo, docJkanime, search);
      }

      // Modificar las keys obtenidas en data (español) -> (inglés)
      animeInfo.setData(AnimeUtils.specialDataKeys(animeInfo.getData(), this.specialKeys));

      return animeInfo;
    } catch (Exception e) {
      log.warning("Error 1: " + e.getMessage());
      throw new AnimeNotFound("Anime no encontrado.");
    }
  }

  private Map<String, Object> getAnimeData(Document docAnimeLife) {
    try {
      Map<String, Object> data = new HashMap<>();

      for (Element item : docAnimeLife.select(".info-content .spe span")) {
        Elements links = item.select("a");
        String key = item.text().split(":")[0].trim().replace(" en", " el");

        if (links.size() == 0 || links == null || links.isEmpty()) {
          if (item.select("time").size() > 0) {
            // Si hay un time, usar el text() como value
            String value = item.select("time").text();

            data.put(key, value);
          } else {
            // Si no hay un time, usar el texto luego de los ":" como value
            String[] values = item.text().split(":");
            if (values.length > 1) {
              String value = values[1].replace(" pero ep.", "").trim();

              // Valores modificables de los values
              if (value.equals("TV")) {
                value = "Anime";
              }
              if (value.equals("Completada")) {
                value = "Finalizado";
              }

              data.put(key, value);
            }
          }
        } else {
          List<LinkDTO> subData = new ArrayList<>();
          for (Element link : links) {
            subData.add(LinkDTO.builder()
              .name(link.text().trim())
              .url(link.attr("href").replace(this.providerAnimeLifeUrl, ""))
              .build());
          }

          data.put(key, subData);
        }
      }

      // ? Modificar los valores de los keys
      // Borra el año
      if (data.containsKey("Año")) {
        data.remove("Año");
      }
      // Si el estado es Proximamente, eliminar la última actualización
      if (data.containsKey("Estado") && data.get("Estado").equals("Proximamente")) {
        data.remove("Actualizado el");
      }
      
      // Establecer los géneros disponibles
      List<LinkDTO> genres = new ArrayList<>();
      for(Element genre : docAnimeLife.select(".genxed a")) {
        genres.add(LinkDTO.builder()
          .name(genre.text().trim())
          .url(genre.attr("href").replace(this.providerAnimeLifeUrl, ""))
          .build());
      }
      data.put("genres", genres);

      return data;
    } catch (Exception e) {
      log.info("Error 2: " + e.getMessage());
      throw new AnimeNotFound("Anime no encontrado.");
    }
  }

  private AnimeInfoDTO setFirstAndLastChapters(AnimeInfoDTO animeInfo, Document docAnimeLife) {
    try {
      // Establecer la fecha del próximo capítulo
      if (animeInfo.getData().get("Estado").equals("En emisión")) {
        animeInfo.setNextChapterDate(DataUtils.parseDate(animeInfo.getData().get("Actualizado el").toString(), 7));
      }
      
      // Establecer la información del primer y último capítulo
      Elements chapters = docAnimeLife.body().select(".eplister ul li");

      if (chapters != null && !chapters.isEmpty()) {
        String lastChapter = chapters.first().select(".epl-num").text().trim();
        String firstChapter = chapters.last().select(".epl-num").text().trim();
        
        animeInfo.setFirstChapter(Integer.parseInt(firstChapter));
        animeInfo.setLastChapter(Integer.parseInt(lastChapter));

        // Asigna la cantidad de capítulos como el último capítulo
        animeInfo.getData().put("Episodios", lastChapter);
      }

      return animeInfo;
    } catch (Exception e) {
      throw new AnimeNotFound("Error obteniendo la información de los capítulos del anime.");
    }
  }

}
