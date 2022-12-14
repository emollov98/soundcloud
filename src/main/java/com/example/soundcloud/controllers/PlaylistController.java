package com.example.soundcloud.controllers;

import com.example.soundcloud.models.dto.playlist.CreatePlaylistDTO;
import com.example.soundcloud.models.dto.playlist.ResponsePLDTO;
import com.example.soundcloud.models.dto.user.APIResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
public class PlaylistController extends GlobalController {

    @GetMapping("/playlist/{playlistId}")
    public ResponsePLDTO getPlaylistById(@PathVariable long playlistId) {
        return playlistService.getPlaylistById(playlistId);
    }

    @GetMapping("/playlist/{offset}/{pageSize}")
    public APIResponse<Page<ResponsePLDTO>> getAllPlaylists(@PathVariable int offset, @PathVariable int pageSize){
        Page<ResponsePLDTO> pages = playlistService.getAllPlaylists(offset,pageSize);
        return new APIResponse<>(pages.getSize(),pages);
    }

    @GetMapping("/playlist/search/n/{playlistName}")
    public List<ResponsePLDTO> searchPlaylistsByName(@PathVariable String playlistName){
        return playlistService.searchPlayListByName(playlistName);
    }

    @GetMapping("/playlist/search/o/{ownerName}")
    public List<ResponsePLDTO> searchPlaylistByUsernamesOwner(@PathVariable String ownerName){
        return playlistService.searchPlaylistByUsernamesOwner(ownerName);
    }

    @PostMapping("/playlist")
    public ResponsePLDTO createPlayList(@RequestBody CreatePlaylistDTO dto, HttpServletRequest req) {
        long userId = getLoggedUserId(req);
        return playlistService.createPlayList(dto, userId);
    }

    @DeleteMapping("playlist/{playlistId}")
    public String deletePlaylist(@PathVariable long playlistId, HttpServletRequest req) {
        long userId = getLoggedUserId(req);
        return playlistService.deletePlaylist(playlistId, userId);
    }

    @PostMapping("/playlist/{playlistId}/{songId}")
    public ResponsePLDTO addSongInPlaylist(@PathVariable long playlistId, @PathVariable long songId, HttpServletRequest req) {
        long userId = getLoggedUserId(req);
        return playlistService.addSongInPlaylist(playlistId, songId, userId);
    }

    @PutMapping("/playlist/{playlistId}/{songId}")
    public ResponsePLDTO removeSongFromPlaylist(@PathVariable long playlistId, @PathVariable long songId, HttpServletRequest req) {
        long userId = getLoggedUserId(req);
        return playlistService.removeSongFromPlayList(playlistId, songId, userId);
    }
}
