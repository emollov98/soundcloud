package com.example.soundcloud.service;

import com.example.soundcloud.models.dto.playlist.CreatePlaylistDTO;
import com.example.soundcloud.models.dto.playlist.ResponsePLDTO;
import com.example.soundcloud.models.dto.song.SongWithoutUserDTO;
import com.example.soundcloud.models.dto.user.UserWithoutPDTO;
import com.example.soundcloud.models.entities.Playlist;
import com.example.soundcloud.models.entities.Song;
import com.example.soundcloud.models.entities.User;
import com.example.soundcloud.models.exceptions.BadRequestException;
import lombok.Synchronized;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PlaylistService extends AbstractService {


    public ResponsePLDTO getPlaylistById(long playlistId) {
        Playlist playlist = findPlaylistById(playlistId);
        ResponsePLDTO dto = modelMapper.map(playlist, ResponsePLDTO.class);
        dto.setOwner(modelMapper.map(dto.getOwner(), UserWithoutPDTO.class));
        return dto;

    }

    @Transactional
    public ResponsePLDTO createPlayList(CreatePlaylistDTO dto, long userId) {
        if (utility.PlaylistNameValidation(dto.getName()) && !utility.isPlaylistExist(dto.getName())) {
            User user = findUserById(userId);
            Playlist playlist = modelMapper.map(dto, Playlist.class);
            playlist.setPlaylistOwner(user);
            playlist.setCreatedAt(LocalDateTime.now());
            playlistRepository.save(playlist);
            ResponsePLDTO respDTO = modelMapper.map(playlist, ResponsePLDTO.class);
            respDTO.setOwner(modelMapper.map(respDTO.getOwner(), UserWithoutPDTO.class));
            return respDTO;
        } else {
            throw new BadRequestException("Title is invalid!");
        }
    }

    @Transactional
    public ResponsePLDTO addSongInPlaylist(long playlistId, long songId, long userId) {
        User owner = findUserById(userId);
        Playlist playlist = findPlaylistById(playlistId);
        Song song = findSongById(songId);
        if (owner.getId() != playlist.getPlaylistOwner().getId()) {
            throw new BadRequestException("Only owner of playlist can add songs. Create your own one to manage it!");
        }
        boolean isHere = false;
        for (Long l : playlistRepository.findAllSongsInPlaylist(playlistId)) {
            if (l.equals(songId)) {
                isHere = true;
                break;
            }
        }
        if (isHere) {
            throw new BadRequestException("Song is already in this playlist!");
        }
        playlist.getSongsInPlaylist().add(song);
        playlistRepository.save(playlist);
        ResponsePLDTO dto = modelMapper.map(playlist, ResponsePLDTO.class);
        dto.setSongsInPlaylist(playlist.getSongsInPlaylist().stream().map(song1 -> modelMapper.map(song1, SongWithoutUserDTO.class)).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public String deletePlaylist(long playlistId, long userId) {
        User owner = findUserById(userId);
        Playlist playlist = findPlaylistById(playlistId);
        if (owner.getId() == playlist.getPlaylistOwner().getId()) {
            playlistRepository.delete(playlist);
            return playlist.getName() + " was successfully deleted!";
        } else {
            throw new BadRequestException("Only owner of playlist cant delete it!");
        }
    }

    @Transactional
    public ResponsePLDTO removeSongFromPlayList(long playlistId, long songId, long userId) {
        Playlist playlist = findPlaylistById(playlistId);
        Song song = findSongById(songId);
        User owner = findUserById(userId);
        if (owner.getId() != playlist.getPlaylistOwner().getId()) {
            throw new BadRequestException("Only owner of playlist can remove songs. Create your own one to manage it!");
        }
        boolean isHere = false;
        for (Long l : playlistRepository.findAllSongsInPlaylist(playlistId)) {
            if (l.equals(songId)) {
                isHere = true;
                break;
            }
        }
        if (!isHere) {
            throw new BadRequestException("Song you wanted to remove is not in the playlist!");
        }
        playlist.getSongsInPlaylist().remove(song);
        playlistRepository.save(playlist);
        ResponsePLDTO dto = modelMapper.map(playlist, ResponsePLDTO.class);
        dto.setSongsInPlaylist(playlist.getSongsInPlaylist().stream().map(song1 -> modelMapper.map(song1, SongWithoutUserDTO.class)).collect(Collectors.toList()));
        return dto;
    }

    public Page<ResponsePLDTO> getAllPlaylists(int offset, int pageSize) {
        Page<ResponsePLDTO> pages = playlistRepository.findAll(PageRequest.of(offset,pageSize)).map(playlist -> modelMapper.map(playlist,ResponsePLDTO.class));
        for (ResponsePLDTO r : pages){
            r.setOwner(modelMapper.map(r.getOwner(),UserWithoutPDTO.class));
        }
        return pages;
    }

    public List<ResponsePLDTO> searchPlayListByName(String playlistName) {
        List<Playlist> playlists = playlistRepository.findPlaylistByName(playlistName);
        List<ResponsePLDTO> dtoPL = playlists.stream().map(playlist -> modelMapper.map(playlist, ResponsePLDTO.class)).collect(Collectors.toList());
        for (ResponsePLDTO r : dtoPL) {
            r.setOwner(modelMapper.map(r.getOwner(), UserWithoutPDTO.class));
            r.setSongsInPlaylist(r.getSongsInPlaylist().stream().map(songWithoutUserDTO -> modelMapper.map(songWithoutUserDTO, SongWithoutUserDTO.class)).collect(Collectors.toList()));
        }
        return dtoPL;
    }

    public List<ResponsePLDTO> searchPlaylistByUsernamesOwner(String ownerName) {
        List<Playlist> playlists = playlistRepository.findPlaylistByOwnerUsername(ownerName);
        List<ResponsePLDTO> dtoPL = playlists.stream().map(playlist -> modelMapper.map(playlist, ResponsePLDTO.class)).collect(Collectors.toList());
        for (ResponsePLDTO r : dtoPL) {
            r.setOwner(modelMapper.map(r.getOwner(), UserWithoutPDTO.class));
            r.setSongsInPlaylist(r.getSongsInPlaylist().stream().map(songWithoutUserDTO -> modelMapper.map(songWithoutUserDTO, SongWithoutUserDTO.class)).collect(Collectors.toList()));
        }
        return dtoPL;
    }
}
