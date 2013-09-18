/*****************************************************************************
 * AudioPlayerFragment.java
 *****************************************************************************
 * Copyright © 2011-2013 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui.audio;

import org.videolan.vlc.AudioServiceController;
import org.videolan.vlc.RepeatType;
import org.videolan.vlc.Util;
import org.videolan.vlc.gui.CommonDialogs;
import org.videolan.vlc.gui.CommonDialogs.MenuType;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.interfaces.IAudioPlayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.tudelft.triblersvod.example.R;

public class AudioPlayerFragment extends SherlockFragment implements IAudioPlayer {
    public final static String TAG = "VLC/AudioPlayerFragment";

    private ImageView mCover;
    private TextView mTitle;
    private TextView mArtist;
    private TextView mAlbum;
    private TextView mTime;
    private TextView mLength;
    private ImageButton mPlayPause;
    private ImageButton mStop;
    private ImageButton mNext;
    private ImageButton mPrevious;
    private ImageButton mShuffle;
    private ImageButton mRepeat;
    private ImageButton mAdvFunc;
    private SeekBar mTimeline;

    private AudioServiceController mAudioController;
    private boolean mShowRemainingTime = false;
    private String lastTitle;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioController = AudioServiceController.getInstance();
        lastTitle = "";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v;

        DisplayMetrics screen = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(screen);
        Log.v(TAG, "width = " + screen.widthPixels + " : height = " + screen.heightPixels);
        if(screen.widthPixels == 240 && screen.heightPixels == 320) /* QVGA 2.7in */
            v = inflater.inflate(R.layout.audio_player_qvga, container, false);
        else
            v = inflater.inflate(R.layout.audio_player, container, false);

        mCover = (ImageView) v.findViewById(R.id.cover);
        mTitle = (TextView) v.findViewById(R.id.title);
        mArtist = (TextView) v.findViewById(R.id.artist);
        mAlbum = (TextView) v.findViewById(R.id.album);
        mTime = (TextView) v.findViewById(R.id.time);
        mLength = (TextView) v.findViewById(R.id.length);
        mPlayPause = (ImageButton) v.findViewById(R.id.play_pause);
        mStop = (ImageButton) v.findViewById(R.id.stop);
        mNext = (ImageButton) v.findViewById(R.id.next);
        mPrevious = (ImageButton) v.findViewById(R.id.previous);
        mShuffle = (ImageButton) v.findViewById(R.id.shuffle);
        mRepeat = (ImageButton) v.findViewById(R.id.repeat);
        mAdvFunc = (ImageButton) v.findViewById(R.id.adv_function);
        mTimeline = (SeekBar) v.findViewById(R.id.timeline);

        mTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTextClick(v);
            }
        });
        mArtist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTextClick(v);
            }
        });
        mAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTextClick(v);
            }
        });
        mTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTimeLabelClick(v);
            }
        });
        mPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlayPauseClick(v);
            }
        });
        mStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStopClick(v);
            }
        });
        mNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onNextClick(v);
            }
        });
        mPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPreviousClick(v);
            }
        });
        mShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onShuffleClick(v);
            }
        });
        mRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onRepeatClick(v);
            }
        });
        mAdvFunc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdvancedOptions(v);
            }
        });

        View.OnFocusChangeListener listener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if(hasFocus)
                    view.setBackgroundColor(Color.parseColor("#FFBA6F"));
                else
                    view.setBackgroundColor(Color.TRANSPARENT);
            }
        };
        mShuffle.setOnFocusChangeListener(listener);
        mRepeat.setOnFocusChangeListener(listener);
        mAdvFunc.setOnFocusChangeListener(listener);
        mTimeline.setOnFocusChangeListener(listener);
        mPrevious.setOnFocusChangeListener(listener);
        mPlayPause.setOnFocusChangeListener(listener);
        mStop.setOnFocusChangeListener(listener);
        mNext.setOnFocusChangeListener(listener);

        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mAudioController.addAudioPlayer(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mAudioController.removeAudioPlayer(this);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /**
     * Show the audio player from an intent
     *
     * @param context The context of the activity
     */
    public static void start(Context context) {
        Intent intent = new Intent();
        intent.setAction(MainActivity.ACTION_SHOW_PLAYER);
        context.getApplicationContext().sendBroadcast(intent);
    }

    @Override
    public void update() {
        // Exit the player and return to the main menu when there is no media
        if (!mAudioController.hasMedia()) {
            getActivity().getSupportFragmentManager().popBackStackImmediate(); // remove this fragment from view
            return;
        }

        String title = mAudioController.getTitle();
        if (title != null && !title.equals(lastTitle)) {
            Bitmap cover = mAudioController.getCover();
            if (cover != null)
                mCover.setImageBitmap(cover);
            else
                mCover.setImageResource(R.drawable.cone);
        }
        lastTitle = title;
        mTitle.setText(lastTitle);
        mArtist.setText(mAudioController.getArtist());
        mAlbum.setText(mAudioController.getAlbum());
        int time = mAudioController.getTime();
        int length = mAudioController.getLength();
        mTime.setText(Util.millisToString(mShowRemainingTime ? time-length : time));
        mLength.setText(Util.millisToString(length));
        mTimeline.setMax(length);
        mTimeline.setProgress(time);
        if (mAudioController.isPlaying()) {
            mPlayPause.setImageResource(R.drawable.ic_pause);
            mPlayPause.setContentDescription(getString(R.string.pause));
        } else {
            mPlayPause.setImageResource(R.drawable.ic_play);
            mPlayPause.setContentDescription(getString(R.string.play));
        }
        if (mAudioController.isShuffling()) {
            mShuffle.setImageResource(R.drawable.ic_shuffle_glow);
        } else {
            mShuffle.setImageResource(R.drawable.ic_shuffle);
        }
        switch(mAudioController.getRepeatType()) {
        case None:
            mRepeat.setImageResource(R.drawable.ic_repeat);
            break;
        case Once:
            mRepeat.setImageResource(R.drawable.ic_repeat_one);
            break;
        default:
        case All:
            mRepeat.setImageResource(R.drawable.ic_repeat_glow);
            break;
        }
        if (mAudioController.hasNext())
            mNext.setVisibility(ImageButton.VISIBLE);
        else
            mNext.setVisibility(ImageButton.INVISIBLE);
        if (mAudioController.hasPrevious())
            mPrevious.setVisibility(ImageButton.VISIBLE);
        else
            mPrevious.setVisibility(ImageButton.INVISIBLE);
        mTimeline.setOnSeekBarChangeListener(mTimelineListner);
    }

    OnSeekBarChangeListener mTimelineListner = new OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onProgressChanged(SeekBar sb, int prog, boolean fromUser) {
            if (fromUser) {
                mAudioController.setTime(prog);
                mTime.setText(Util.millisToString(mShowRemainingTime ? prog-mAudioController.getLength() : prog))
            ;
        }
    }
    };

    public void onTimeLabelClick(View view) {
        mShowRemainingTime = !mShowRemainingTime;
        update();
    }

    public void onTextClick(View view) {
       Toast.makeText(getActivity(), "Open playlist view", Toast.LENGTH_SHORT).show();
    }

    public void onPlayPauseClick(View view) {
        if (mAudioController.isPlaying()) {
            mAudioController.pause();
        } else {
            mAudioController.play();
        }
    }

    public void onStopClick(View view) {
        mAudioController.stop();
        getActivity().getSupportFragmentManager().popBackStack(); // remove this fragment from view
    }

    public void onNextClick(View view) {
        mAudioController.next();
    }

    public void onPreviousClick(View view) {
        mAudioController.previous();
    }

    public void onRepeatClick(View view) {
        switch (mAudioController.getRepeatType()) {
            case None:
                mAudioController.setRepeatType(RepeatType.All);
                break;
            case All:
                mAudioController.setRepeatType(RepeatType.Once);
                break;
            default:
            case Once:
                mAudioController.setRepeatType(RepeatType.None);
                break;
        }
        update();
    }

    public void onShuffleClick(View view) {
        mAudioController.shuffle();
        update();
    }

/* TODO
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        /* Stop the controller if we are going home /
        if(keyCode == KeyEvent.KEYCODE_HOME) {
            mAudioController.stop();
        }
        return super.onKeyDown(keyCode, event);
    }
*/

    public void showAdvancedOptions(View v) {
        CommonDialogs.advancedOptions(getActivity(), v, MenuType.Audio);
    }
}
