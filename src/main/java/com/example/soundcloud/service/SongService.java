package com.example.soundcloud.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.example.soundcloud.models.dao.SongDAO;
import com.example.soundcloud.models.dto.DislikeDTO;
import com.example.soundcloud.models.dto.LikeDTO;
import com.example.soundcloud.models.dto.song.*;
import com.example.soundcloud.models.dto.user.UserWithoutPDTO;
import com.example.soundcloud.models.dto.user.UserWithoutPWithSongsDTO;
import com.example.soundcloud.models.entities.Song;
import com.example.soundcloud.models.entities.User;
import com.example.soundcloud.models.entities.listeners.Listened;
import com.example.soundcloud.models.entities.listeners.ListenedKey;
import com.example.soundcloud.models.exceptions.BadRequestException;
import com.example.soundcloud.models.exceptions.FileException;
import com.example.soundcloud.models.exceptions.MethodNotAllowedException;
import com.example.soundcloud.models.exceptions.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.FilenameUtils;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SongService extends AbstractService {

    public static final int SONGS_PER_PAGE = 5;
    public static final int FIRST_PAGE = 1;
    private static final long MAX_FILESIZE = 100 * 1024 * 1024;
    private static final String STORAGE_BUCKET_NAME = "soundcloudtalents";


    private final SongDAO songDAO;
    private final AmazonS3 storageClient;

    @Autowired
    public SongService(SongDAO songDAO, AmazonS3 storageClient) {
        this.songDAO = songDAO;
        this.storageClient = storageClient;
    }


    public List<ResponseGetSongDTO> searchByGenre(String genre) {
        List<Song> songs = this.songRepository.findAllByGenre(genre);
        if (songs.size() == 0) {
            throw new NotFoundException("There is no musical content for genre: " + genre + ".");
        }
        return songs.stream().map(song -> modelMapper.map(song, ResponseGetSongDTO.class)).collect(Collectors.toList());
    }

    public List<ResponseGetSongByUsernameDTO> searchByUploader(long uid) {
        User uploader = findUserById(uid);
        List<Song> songs = songRepository.findAllByUploader(uploader);
        if (songs.size() == 0) {
            throw new NotFoundException("This user has not uploaded any songs!");
        }
        return songs.stream().map(song -> modelMapper.map(song, ResponseGetSongByUsernameDTO.class)).collect(Collectors.toList());
    }

    public List<ResponseGetSongDTO> searchLikedSongsByUser(String username) {
        Optional<User> optionalUser = this.userRepository.findUserByUsername(username);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            List<Song> songs = user.getLikedSongs();
            if (songs.size() == 0) {
                throw new NotFoundException("This user has not liked any songs!");
            }
            return songs.stream().map(song -> modelMapper.map(song, ResponseGetSongDTO.class)).collect(Collectors.toList());
        } else {
            throw new NotFoundException("User: " + username + " doesnt exist!");
        }
    }

    public List<ResponseGetSongDTO> searchByTitle(String title) {
        List<Song> songs = songRepository.findSongByCharSequence(title).stream().collect(Collectors.toList());
        List<ResponseGetSongDTO> songsDTO = songs.stream().map(song -> modelMapper.map(song, ResponseGetSongDTO.class)).collect(Collectors.toList());
        return songsDTO;
    }

    public List<ResponseSongFilterDTO> filterSongs(RequestSongFilterDTO filterType) throws SQLException {
        String title = filterType.getTitle();
        if (title == null) {
            throw new BadRequestException("You can not search without a title!");
        }

        String filterBy = filterType.getFilterBy();
        if (filterBy == null) {
            filterBy = "likes";
        } else {
            filterBy = filterBy.toLowerCase().trim();
        }

        String orderBy = filterType.getOrderBy();
        if (orderBy == null) {
            orderBy = "asc";
        } else {
            orderBy = orderBy.toLowerCase().trim();
            if (!orderBy.equals("asc") && !orderBy.equals("desc")) {
                throw new BadRequestException("Invalid type of ordering!");
            }
        }

        Integer page = filterType.getPage();
        if (page == null || page == 0) {
            page = FIRST_PAGE;
        }

        switch (filterBy) {
            case "likes":
            case "dislikes":
            case "upload_date":
            case "listened":
            case "comments":
                return songDAO.filter(title, filterBy, orderBy, page, SONGS_PER_PAGE);
            default:
                throw new BadRequestException("Invalid type of filter!");
        }
    }

    public LikeDTO like(long sid, long uid) {
        Song song = findSongById(sid);
        User user = findUserById(uid);
        if (user.getLikedSongs().contains(song)) {
            user.getLikedSongs().remove(song);
            userRepository.save(user);
            return new LikeDTO("Your like was successfully removed!", song.getLikers().size());
        } else {
            user.getLikedSongs().add(song);
            userRepository.save(user);
            return new LikeDTO("Your like was successfully accepted!", song.getLikers().size());
        }
    }

    public DislikeDTO dislike(long sid, long uid) {
        Song song = findSongById(sid);
        User user = findUserById(uid);
        if (user.getDislikedSongs().contains(song)) {
            user.getDislikedSongs().remove(song);
            userRepository.save(user);
            return new DislikeDTO("Your dislike was successfully removed!", song.getDislikers().size());
        } else {
            user.getDislikedSongs().add(song);
            userRepository.save(user);
            return new DislikeDTO("Your dislike was successfully accepted!", song.getDislikers().size());
        }
    }

    public void isSongDisliked(long sid, long uid) {
        Song song = findSongById(sid);
        User currentUser = modelMapper.map(userRepository.findById(uid), User.class);
        if (song.getDislikers().contains(currentUser)) {
            currentUser.getDislikedSongs().remove(song);
        }
    }

    public void isSongLiked(long sid, long uid) {
        Song song = findSongById(sid);
        User currentUser = modelMapper.map(userRepository.findById(uid), User.class);
        if (song.getLikers().contains(currentUser)) {
            currentUser.getLikedSongs().remove(song);
        }
    }

    public ResponseSongDTO getSongWithUserById(long sid) {
        Song song = findSongById(sid);
        ResponseSongDTO dto = modelMapper.map(song, ResponseSongDTO.class);
        dto.setUploader(modelMapper.map(dto.getUploader(), UserWithoutPDTO.class));
        return dto;
    }

    public ResponseSongUploadDTO uploadSong(long uid, String title, String artist, String genre, String description, MultipartFile songFile) {
    User currentUser = findUserById(uid);
    String extension = FilenameUtils.getExtension(songFile.getOriginalFilename());
    String nameUrl = "uploadedSongs" + File.separator + System.nanoTime() + "." + extension;
        if (!extension.equals("mp3")) {
        throw new BadRequestException("You are trying to upload an invalid file. You have to select an mp3 file.");
    }
        if (songFile.getSize() > MAX_FILESIZE) {
        throw new BadRequestException("The size of the song is too large.");
    }

    Song uploadedSong = new Song();
    ObjectMetadata metaData = new ObjectMetadata();
        metaData.setContentType("audio/mpeg");
        metaData.setContentLength(songFile.getSize());

        if (songUploadValidation(title, artist, genre)) {
        File f = new File(nameUrl);
        if (!f.exists()) {
            try {
                Files.copy(songFile.getInputStream(), f.toPath());
            } catch (IOException e) {
                throw new BadRequestException(e.getMessage(), e);
            }
        } else {
            throw new BadRequestException("The file already exists!");
        }

        try {
            this.storageClient.putObject(STORAGE_BUCKET_NAME, nameUrl, songFile.getInputStream(), metaData);
            uploadedSong.setUploader(currentUser);
            uploadedSong.setTitle(title);
            uploadedSong.setArtist(artist);
            uploadedSong.setGenre(genre);
            uploadedSong.setCreatedAt(LocalDateTime.now());
            uploadedSong.setListened(0);
            uploadedSong.setUrl(nameUrl);
            if (description != null) {
                uploadedSong.setDescription(description);
            }
            this.songRepository.save(uploadedSong);
        } catch (AmazonServiceException | IOException e) {
            throw new FileException("Problem with the uploading of the song to the server - " + e.getMessage());
        }
    }
        return modelMapper.map(uploadedSong, ResponseSongUploadDTO.class);
}


    public ResponseSongDeleteDTO deleteSong(long uid, long sid) {
        Song songToDelete = findSongById(sid);
        User user = findUserById(uid);
        if (user.getId() == songToDelete.getUploader().getId()) {
            File fileToDelete = new File(songToDelete.getUrl());
            fileToDelete.delete();
            songRepository.delete(songToDelete);
            storageClient.deleteObject(STORAGE_BUCKET_NAME, songToDelete.getUrl());
            return new ResponseSongDeleteDTO("Song deleted successfully!", sid);
        } else {
            throw new MethodNotAllowedException("The song that you are trying to delete was not uploaded by you!");
        }
    }


    public ResponseGetSongInfoDTO editSong(RequestSongEditDTO dto, long uid, long sid) {
        User user = findUserById(uid);
        Song song = findSongById(sid);

        if (songEditValidation(dto)) {
            if (user.getId() == song.getUploader().getId()) {
                setSongEdit(dto, song);
                songRepository.save(song);
                return modelMapper.map(song, ResponseGetSongInfoDTO.class);
            } else {
                throw new MethodNotAllowedException("The song that you are trying to edit was not uploaded by you!");
            }
        } else {
            throw new BadRequestException("Invalid input!");
        }
    }

    private void setSongEdit(RequestSongEditDTO dto, Song song) {
        song.setTitle(dto.getTitle());
        song.setArtist(dto.getArtist());
        song.setGenre(dto.getGenre());
        song.setDescription(dto.getDescription());
    }


    protected boolean songUploadValidation(String title, String artist, String genre) {
        if (titleValidation(title) &&
                artistValidation(artist) &&
                genreValidation(genre)) {
            return true;
        } else {
            throw new BadRequestException("Invalid song data!");
        }
    }

    protected boolean songEditValidation(RequestSongEditDTO song) {
        if (titleValidation(song.getTitle()) &&
                artistValidation(song.getArtist()) &&
                genreValidation(song.getGenre())) {
            return true;
        } else {
            throw new BadRequestException("Invalid song data!");
        }
    }

    protected boolean titleValidation(String title) {
        String regex = "^[a-zA-Z0-9_ !$%^&*-`)(]{2,40}$";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(title);
        boolean isMatching = m.matches();
        if (!title.isBlank() && isMatching) {
            return true;
        } else {
            throw new BadRequestException("The title is invalid!");
        }
    }

    protected boolean genreValidation(String genre) {
        String regex = "^[A-Za-z\\s-]{2,29}$"; // it allows to use upper/lower case spaces and -
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(genre);
        boolean isMatching = m.matches();
        if (!genre.isBlank() && isMatching) {
            return true;
        } else {
            throw new BadRequestException("The genre is invalid!");
        }
    }

    protected boolean artistValidation(String artist) {
        String regex = "^[a-zA-Z0-9_ !$%^&*)(]{2,40}$";// artist may contains only characters between A-Z/a-z, digits 0-9 and special symbols as space,!$%^&*() !
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(artist);
        boolean isMatching = m.matches();
        if (!artist.isBlank() && isMatching) {
            return true;
        } else {
            throw new BadRequestException("The artist is invalid! It may contains uppercase and lowercase letters, numbers and special characters as !$%^&*()!");
        }
    }


    public void play(long sid, long userId, HttpServletResponse response) {
        Song song = findSongById(sid);
        Optional<User> user = userRepository.findById(userId);
        ListenedKey listenedKey = new ListenedKey();
        listenedKey.setSongId(song.getId());
        Listened listened = new Listened();
        listened.setSong(song);
        boolean isHere = false;
        if (user.isPresent()) {
            listened.setUser(user.get());
            listenedKey.setUserId(user.get().getId());
            for (Listened l : song.getListeners()) {
                if (l.getSong().getId() == song.getId() && l.getUser().getId() == user.get().getId()) {
                    isHere = true;
                    listened = l;
                    break;
                }
            }
            if (!isHere) {
                song.setListened(song.getListened() + 1);
                listened.setId(listenedKey);
                listened.setListened(listened.getListened() + 2);
                listenedRepository.save(listened);
                songRepository.save(song);
            } else {
                listened.setId(listenedKey);
                listened.setListened(listened.getListened() + 1);
                listenedRepository.save(listened);
            }
        } else {
            song.setListened(song.getListened() + 1);
            songRepository.save(song);
        }
        String nameOfFile = song.getUrl().substring(song.getUrl().indexOf(File.separator));
        System.out.println(nameOfFile);
        File songToPlay = new File("uploadedSongs" + File.separator + nameOfFile);
        if (!songToPlay.exists()) {
            throw new NotFoundException("Song does not exist");
        } else {
            try {
                response.setContentType(Files.probeContentType(songToPlay.toPath()));
                Files.copy(songToPlay.toPath(), response.getOutputStream());
            } catch (IOException e) {
                throw new BadRequestException("Problem with output stream.");
            }
        }
    }

    public byte[] downloadSong(@PathVariable String songName) {
        S3Object songFile = storageClient.getObject(STORAGE_BUCKET_NAME, songName);
        S3ObjectInputStream inputStream = songFile.getObjectContent();

        try {
            byte[] content = IOUtils.toByteArray(inputStream);
            return content;
        } catch (AmazonServiceException | IOException e) {
            throw new FileException("Problem with song downloading - " + e.getMessage());
        }
    }


    public ResponseGetSongInfoDTO getSongInfo(long sid) {
        Song song = findSongById(sid);
        ResponseGetSongInfoDTO dto = modelMapper.map(song, ResponseGetSongInfoDTO.class);
        dto.setLikes(song.getLikers().size());
        dto.setDislikes(song.getDislikers().size());
        dto.setComments(song.getComments().size());
        return dto;
    }

    public Page<ResponseSongDTO> sortSongWithPagination(int offset, int pageSize, String sortedBy) {
        Page<ResponseSongDTO> songs = songRepository
                .findAll(PageRequest.of(offset, pageSize).withSort(Sort.by(sortedBy)))
                .map(song -> modelMapper.map(song, ResponseSongDTO.class));
        return songs;
    }

    public Page<ResponseSongDTO> searchSong(String keyword, int offset, int pageSize, String sortedBy, String kindOfSort) {
        if (kindOfSort.equalsIgnoreCase("asc")) {
            Page<ResponseSongDTO> dto = songRepository
                    .findByKeyword(keyword, PageRequest.of(offset, pageSize)
                            .withSort(Sort.by(sortedBy)))
                    .map(song -> modelMapper.map(song, ResponseSongDTO.class));
            return dto;
        } else {
            Page<ResponseSongDTO> dto = songRepository
                    .findByKeyword(keyword, PageRequest.of(offset, pageSize)
                            .withSort(Sort.by(sortedBy).descending()))
                    .map(song -> modelMapper.map(song, ResponseSongDTO.class));
            return dto;
        }
    }

    public List<ResponseSongFilterDTO> topGenreSongsForUser(long uid, int page) throws SQLException {
        if (page == 0) {
            page = 1;
        }
        return songDAO.findSongsByGenreForUser(uid, page, SONGS_PER_PAGE);
    }

    public List<ResponseSongFilterDTO> topGenreSongs(int page) throws SQLException {
        if (page == 0) {
            page = 1;
        }
        return songDAO.findSongsByGenre(page, SONGS_PER_PAGE);
    }

    public List<ResponseSongFilterDTO> topListened(int page) throws SQLException {
        if (page == 0) {
            page = 1;
        }
        return songDAO.findTopListened(page, SONGS_PER_PAGE);
    }
}
