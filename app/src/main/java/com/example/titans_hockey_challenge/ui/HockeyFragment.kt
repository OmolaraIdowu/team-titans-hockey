package com.example.titans_hockey_challenge.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.titans_hockey_challenge.R
import com.example.titans_hockey_challenge.ThemeViewModel
import com.example.titans_hockey_challenge.databinding.FragmentHockeyBinding
import com.example.titans_hockey_challenge.models.HockeyTable
import com.example.titans_hockey_challenge.utils.GameThread

class HockeyFragment : Fragment() {
    private var mGameThread: GameThread? = null
    private lateinit var hockeyTable: HockeyTable

    private var _binding: FragmentHockeyBinding? = null
    private val binding get() = _binding !!

    private val themeViewModel: ThemeViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        _binding = FragmentHockeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        hockeyTable = binding.hockeyTable

        hockeyTable.setScoreOpponent(binding.tvScoreOpponent)
        hockeyTable.setScorePlayer(binding.tvScorePlayer)
        hockeyTable.setStatus(binding.tvStatus)
        mGameThread = hockeyTable.game

        binding.imgPauseButton.setOnClickListener {
            mGameThread?._pause()
            hockeyTable.pauseBackgroundSound()
            binding.pauseOverlay.visibility = View.VISIBLE
        }

        binding.resumeGame.setOnClickListener {
            mGameThread?._resume()
            hockeyTable.resumeGame()
            hockeyTable.playStartGameSound()
            binding.pauseOverlay.visibility = View.INVISIBLE
        }

        binding.exitGame.setOnClickListener {
            findNavController().popBackStack(R.id.gameFragment, false)
        }

        themeViewModel.puckColor.observe(viewLifecycleOwner) { color ->
            hockeyTable.setPuckColor(color)
        }

        themeViewModel.puckInnerColor.observe(viewLifecycleOwner) { color ->
            hockeyTable.setPuckInnerColor(color)
        }

        themeViewModel.paddleColor.observe(viewLifecycleOwner) { color ->
            hockeyTable.setPaddleColor(color)
        }

        themeViewModel.paddleMiddleColor.observe(viewLifecycleOwner) { color ->
            hockeyTable.setPaddleMiddleColor(color)
        }

        themeViewModel.paddleOuterColor.observe(viewLifecycleOwner) { color ->
            hockeyTable.setPaddleOuterColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}