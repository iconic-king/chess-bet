package chessbet.app.com;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import chessbet.adapter.ComputerSkillLevelAdapter;
import chessbet.app.com.activities.MainActivity;
import chessbet.domain.MatchType;


public class PlayComputerSettingsActivity extends AppCompatActivity implements View.OnClickListener, ComputerSkillLevelAdapter.SkillLevel {
    @BindView(R.id.levels)
    GridView gridView;
    @BindView(R.id.btnCancel)
    Button btnCancel;
    @BindView(R.id.btnPlay) Button btnPlay;
    private long skillLevel = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play_computer_settings);
        ButterKnife.bind(this);
        btnCancel.setOnClickListener(this);
        btnPlay.setOnClickListener(this);
        gridView.setAdapter(new ComputerSkillLevelAdapter(this, this));
    }

    @Override
    public void onClick(View view) {
        if(view.equals(btnCancel)) {
            Intent intent=new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } else if (view.equals(btnPlay)) {
            if(skillLevel != 0) {
                Intent intent = new Intent(this, BoardActivity.class);
                intent.putExtra("match_type", MatchType.SINGLE_PLAYER.toString());
                intent.putExtra("skill_level", this.skillLevel);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Set Skill Level", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void setSkillLevel(int skillLevel) {
        this.skillLevel = skillLevel;
    }
}
