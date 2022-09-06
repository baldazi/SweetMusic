package com.example.musicplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SelectedAlbumActivity : Tools(), MusicList.OnMusicListener, SearchView.OnQueryTextListener{
    private lateinit var album : Album
    private var albumPosition : Int = 0
    private lateinit var adapter : MusicList
    private var musics = ArrayList<Music>()
    private var allMusicsBackup = ArrayList<Music>()
    private lateinit var searchView : SearchView
    private var searchIsOn = false
    private lateinit var menuRecyclerView : RecyclerView

    private lateinit var onAudioFocusChange : AudioManager.OnAudioFocusChangeListener
    private lateinit var audioAttributes : AudioAttributes
    private lateinit var audioManager : AudioManager

    private lateinit var pausePlayButton : ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selected_album)

        pausePlayButton = findViewById(R.id.pause_play)
        menuRecyclerView = findViewById(R.id.menu_album_recycler_view)
        albumPosition = intent.getSerializableExtra("POSITION") as Int

        album = MyMediaPlayer.allAlbums[albumPosition]
        musics = album.albumList
        allMusicsBackup = ArrayList(musics.map { it.copy() })

        searchView = findViewById(R.id.search_view)
        searchView.setOnQueryTextListener(this)

        adapter = MusicList(musics, album.albumName, applicationContext, this@SelectedAlbumActivity)
        menuRecyclerView.layoutManager = LinearLayoutManager(this@SelectedAlbumActivity)
        menuRecyclerView.adapter = adapter

        val albumName = findViewById<TextView>(R.id.album_name)
        albumName?.text = album.albumName

        val noSongPlaying = findViewById<TextView>(R.id.no_song_playing)
        val infoSongPlaying = findViewById<RelativeLayout>(R.id.info_song_playing)
        val songTitleInfo = findViewById<TextView>(R.id.song_title_info)
        val bottomInfos = findViewById<LinearLayout>(R.id.bottom_infos)
        val albumCoverInfo = findViewById<ImageView>(R.id.album_cover_info)

        if (MyMediaPlayer.currentIndex == -1){
            noSongPlaying.visibility = View.VISIBLE
            infoSongPlaying.visibility = View.GONE
        } else {
            noSongPlaying.visibility = View.GONE
            infoSongPlaying.visibility = View.VISIBLE
            songTitleInfo?.text = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].name
            if (MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover != null){
                // Passons d'abord notre byteArray en bitmap :
                val bytes = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover
                var bitmap: Bitmap? = null
                if (bytes != null && bytes.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                albumCoverInfo.setImageBitmap(bitmap)
            } else {
                albumCoverInfo.setImageResource(R.drawable.michael)
            }

            bottomInfos.setOnClickListener{onBottomMenuClick(MyMediaPlayer.currentIndex, this@SelectedAlbumActivity) }
            songTitleInfo?.isSelected = true
        }

        val shuffleButton = findViewById<Button>(R.id.shuffle_button)
        shuffleButton.setOnClickListener { playRandom(musics, this@SelectedAlbumActivity) }

        mediaPlayer.setOnCompletionListener { playNextSong(adapter) }

        audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        onAudioFocusChange = AudioManager.OnAudioFocusChangeListener { focusChange ->
            Log.d("focusChange", focusChange.toString())
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> println("gain")
                else -> {
                    if (mediaPlayer.isPlaying) {
                        println("loss focus")
                        mediaPlayer.pause()
                        pausePlayButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
                    }
                    Log.d("change does..", "")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        searchView.clearFocus()

        allMusicsBackup = MyMediaPlayer.allAlbums[albumPosition].albumList
        if(!searchIsOn){
            musics = MyMediaPlayer.allAlbums[albumPosition].albumList
            adapter.musics = musics
        }

        adapter.notifyDataSetChanged()

        val noSongPlaying = findViewById<TextView>(R.id.no_song_playing)
        val infoSongPlaying = findViewById<RelativeLayout>(R.id.info_song_playing)
        val songTitleInfo = findViewById<TextView>(R.id.song_title_info)
        val nextBtn = findViewById<ImageView>(R.id.next)
        val previousBtn = findViewById<ImageView>(R.id.previous)
        val bottomInfos = findViewById<LinearLayout>(R.id.bottom_infos)
        val albumCoverInfo = findViewById<ImageView>(R.id.album_cover_info)

        noSongPlaying.visibility = View.VISIBLE

        if (MyMediaPlayer.currentIndex != -1){
            noSongPlaying.visibility = View.GONE
            infoSongPlaying.visibility = View.VISIBLE
            songTitleInfo.text = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].name
            if (MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover != null){
                // Passons d'abord notre byteArray en bitmap :
                val bytes = MyMediaPlayer.currentPlaylist[MyMediaPlayer.currentIndex].albumCover
                var bitmap: Bitmap? = null
                if (bytes != null && bytes.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                albumCoverInfo.setImageBitmap(bitmap)
            } else {
                albumCoverInfo.setImageResource(R.drawable.michael)
            }

            pausePlayButton.setOnClickListener{ pausePlay() }
            nextBtn?.setOnClickListener{ playNextSong(adapter) }
            previousBtn?.setOnClickListener{ playPreviousSong(adapter) }
            bottomInfos.setOnClickListener{ onBottomMenuClick(MyMediaPlayer.currentIndex, this@SelectedAlbumActivity) }
            songTitleInfo?.isSelected = true
        }

        if (!mediaPlayer.isPlaying){
            pausePlayButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
        } else {
            pausePlayButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
        }

        mediaPlayer.setOnCompletionListener { playNextSong(adapter) }
    }

    override fun onMusicClick(position: Int) {
        var sameMusic = true

        if (position != MyMediaPlayer.currentIndex) {
            sameMusic = false
        }
        // Vérifions si on change de playlist :
        if (musics != MyMediaPlayer.initialPlaylist) {
            MyMediaPlayer.currentPlaylist = ArrayList(musics.map { it.copy() })
            MyMediaPlayer.initialPlaylist = ArrayList(musics.map { it.copy() })
            MyMediaPlayer.playlistName = "Album"
            sameMusic = false
        }

        MyMediaPlayer.currentIndex = position
        val intent = Intent(this@SelectedAlbumActivity,MusicPlayerActivity::class.java)

        intent.putExtra("SAME MUSIC", sameMusic)
        intent.putExtra("ALBUM POSITION", albumPosition)
        println("start mp activity")

        startActivity(intent)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            0 -> {
                Toast.makeText(this,"Ajout dans une playlist",Toast.LENGTH_SHORT).show()
                true
            }
            1 -> {
                val musicToRemove = adapter.musics[item.groupId]
                musics.removeAt(item.groupId)
                adapter.notifyItemRemoved(item.groupId)
                MyMediaPlayer.allMusics.remove(musicToRemove)

                GlobalScope.launch(Dispatchers.IO){
                    launch{writeAllMusicsToFile(saveAllMusicsFile,MyMediaPlayer.allMusics)}
                }

                Toast.makeText(this,"Suppressions de la musique dans la playlist",Toast.LENGTH_SHORT).show()
                true
            }
            2 -> {
                // MODIFY INFOS :
                val intent = Intent(this@SelectedAlbumActivity,ModifyMusicInfoActivity::class.java)
                intent.putExtra("PATH", musics[item.groupId].path)
                resultModifyMusic.launch(intent)
                true
            }
            3 -> {
                // Lorsque l'on veut jouer une musique après celle qui se joue actuellement, on supprime d'abord la musique de la playlist :
                MyMediaPlayer.initialPlaylist.remove((musics[item.groupId]))
                MyMediaPlayer.currentPlaylist.remove((musics[item.groupId]))

                MyMediaPlayer.currentPlaylist.add(MyMediaPlayer.currentIndex+1, musics[item.groupId])
                true
            }
            else -> {
                onContextItemSelected(item)
            }
        }
    }

    private var resultModifyMusic = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onQueryTextSubmit(p0: String?): Boolean {
        return manageSearchBarEvents(p0)
    }

    override fun onQueryTextChange(p0: String?): Boolean {
        return manageSearchBarEvents(p0)
    }

    private fun manageSearchBarEvents(p0 : String?) : Boolean {
        try {
            if (p0 != null) {
                val list = ArrayList<Music>()

                if(p0 == ""){
                    searchIsOn = false
                    musics = allMusicsBackup
                    adapter.musics = musics
                    adapter.notifyDataSetChanged()
                } else {
                    searchIsOn = true
                    for (music: Music in allMusicsBackup) {
                        if ((music.name.lowercase().contains(p0.lowercase())) || (music.album.lowercase().contains(p0.lowercase())) || (music.artist.lowercase().contains(p0.lowercase()))){
                            list.add(music)
                        }
                    }

                    if (list.size > 0) {
                        musics = list
                        adapter.musics = musics
                        adapter.notifyDataSetChanged()
                    } else {
                        musics = ArrayList<Music>()
                        adapter.musics = musics
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        } catch (error : Error){
            Log.d("ERROR",error.toString())
        }
        return true
    }

    private fun pausePlay() {
        val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(onAudioFocusChange)
            .build()

        when (audioManager.requestAudioFocus(audioFocusRequest)) {
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
                Toast.makeText(this,"Cannot launch the music", Toast.LENGTH_SHORT).show()
            }

            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                if(mediaPlayer.isPlaying){
                    mediaPlayer.pause()
                    pausePlayButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_24)
                } else {
                    mediaPlayer.start()
                    pausePlayButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24)
                }
            }
            else -> {
                Toast.makeText(this,"AN unknown error has come up", Toast.LENGTH_SHORT).show()
            }
        }
    }
}