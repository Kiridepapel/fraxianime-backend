package xyz.kiridepapel.fraxianimebackend.service;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.java.Log;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.ChapterDTO;
import xyz.kiridepapel.fraxianimebackend.dto.AnimeInfoDTO;

@Service
@Log
public class AnimeInfoService {
  @Value("${PROVEEDOR_LIMITED_URL}")
  private String proveedorLimitedUrl;

  public AnimeInfoDTO getAnimeInfo(String search) {
    String urlBase = this.proveedorLimitedUrl;
    String urlAnimeInfo = urlBase + search.replaceAll("/\\d+$", "");

    Document docAnimeInfo = null;
    docAnimeInfo = ZMethods.connectAnimeInfo(docAnimeInfo, urlAnimeInfo, "No se encontró el anime solicitado.");
    
    String chaptersImgUrl = docAnimeInfo.select(".lazy").attr("src");
    AnimeInfoDTO animeInfo = AnimeInfoDTO.builder()
      .name(docAnimeInfo.select(".Title").first().text().trim())
      .sinopsis(docAnimeInfo.select(".Description p").text())
      .imgUrl(docAnimeInfo.select(".anime-image").attr("src"))
      .chapters(this.getChapters(docAnimeInfo, chaptersImgUrl))
      .genres(this.getGenres(docAnimeInfo))
      .build();

    return animeInfo;
  }

  private List<ChapterDTO> getChapters(Document document, String chaptersImgUrl) {
    Elements elements = document.select(".fa-play-circle");
    List<ChapterDTO> chapters = new ArrayList<>();
    String aux = "";

    for (Element element : elements) {
      try {
        String url = element.select("a").attr("href")
          .replace(this.proveedorLimitedUrl, "")
          .replace("-episodio-", "/").trim();
          
        String chapter = "Capitulo " + this.getChapterNumberFromUrl(url, false);

        if (aux == chapter) {
          chapter = "Capitulo " + (this.getChapterNumberFromUrl(url + "-1", true));
        }

        log.info("url: " + url);
        log.info("chapter: " + chapter);
        log.info("--------------------");
        
        if (ZMethods.isNotNullOrEmpty(url)) {
          ChapterDTO anime = ChapterDTO.builder()
            .url(url)
            .chapter(chapter)
            .imgUrl(chaptersImgUrl)
            .build();
          
          chapters.add(anime);
        }

        aux = chapter;
      } catch (Exception e) {
        log.info("Error: " + e.getMessage());
      }
    }

    return chapters;
  }

  private List<String> getGenres(Document document) {
    Elements elements = document.select(".Nvgnrs a");
    List<String> genres = new ArrayList<>();

    for (Element element : elements) {
      genres.add(element.text());
    }

    return genres;
  }

  private String getChapterNumberFromUrl(String uri, boolean bypassProtection) {
    String supossedNumber = uri.replaceAll(".*/(\\d+)", "$1");

    if (supossedNumber.contains("-") && !bypassProtection) {
      return String.valueOf((Integer.parseInt(supossedNumber.split("-")[0]) + 1));
    }
    
    return supossedNumber;
  }
    
}
