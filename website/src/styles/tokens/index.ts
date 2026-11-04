import tokens from './tokens.json' with { type: 'json' };

export { tokens };
export type Tokens = typeof tokens;
export type ColorToken = keyof Tokens['color']['light'];
