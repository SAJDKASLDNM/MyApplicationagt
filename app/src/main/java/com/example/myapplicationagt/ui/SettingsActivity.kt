package com.example.myapplicationagt.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.example.myapplicationagt.R
import com.example.myapplicationagt.data.preferences.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 设置界面活动
 */
class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        
        // 设置标题和返回按钮
        supportActionBar?.apply {
            title = "抖音助手设置"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    /**
     * 设置界面Fragment
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        
        private lateinit var settingsManager: SettingsManager
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // 初始化设置管理器
            settingsManager = SettingsManager(requireContext())
            
            // 加载设置值并设置监听器
            setupPreferences()
        }
        
        /**
         * 设置每个偏好项
         */
        private fun setupPreferences() {
            lifecycleScope.launch {
                try {
                    // 养号设置 - 点赞概率
                    val likeProbPref = findPreference<SeekBarPreference>("like_probability")
                    likeProbPref?.value = settingsManager.likeProbability.first()
                    likeProbPref?.setOnPreferenceChangeListener { _, newValue ->
                        lifecycleScope.launch {
                            settingsManager.updateLikeProbability(newValue as Int)
                        }
                        true
                    }
                    
                    // 养号设置 - 评论概率
                    val commentProbPref = findPreference<SeekBarPreference>("comment_probability")
                    commentProbPref?.value = settingsManager.commentProbability.first()
                    commentProbPref?.setOnPreferenceChangeListener { _, newValue ->
                        lifecycleScope.launch {
                            settingsManager.updateCommentProbability(newValue as Int)
                        }
                        true
                    }
                    
                    // 其他设置项...
                    
                    // 重置按钮
                    val resetPref = findPreference<Preference>("reset_settings")
                    resetPref?.setOnPreferenceClickListener {
                        lifecycleScope.launch {
                            settingsManager.resetToDefaults()
                            Toast.makeText(requireContext(), "设置已重置", Toast.LENGTH_SHORT).show()
                            // 重新加载设置
                            setupPreferences()
                        }
                        true
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "载入设置失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}