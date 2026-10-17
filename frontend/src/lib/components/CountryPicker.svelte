<script lang="ts">
  import { COUNTRIES, COUNTRY_LIST, type Country } from '$lib/showtimes';

  let { country, onchange }: { country: Country; onchange: (country: Country) => void } = $props();
</script>

<div class="field picker">
  <label for="location">Search by location</label>
  <select id="location" value={country} onchange={(e) => onchange(e.currentTarget.value as Country)}>
    {#each COUNTRY_LIST as value (value)}
      <option {value}>{COUNTRIES[value].label}</option>
    {/each}
  </select>
</div>

<style>
  .picker { max-width: 260px; margin: 0; }
</style>
